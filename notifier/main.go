package main

import (
	"bytes"
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"mime"
	"mime/multipart"
	"net/http"
	"net/mail"
	"net/smtp"
	"net/textproto"
	"os"
	"path/filepath"
	"strings"
	"time"
)

type ecsLog struct {
	Timestamp string         `json:"@timestamp"`
	Log       ecsLogLog      `json:"log"`
	Process   ecsLogProcess  `json:"process"`
	Service   ecsLogService  `json:"service"`
	Message   string         `json:"message"`
	Ecs       ecsLogEcs      `json:"ecs,omitempty"`
	Trace     *ecsLogTrace   `json:"trace,omitempty"`
	Error     *ecsLogError   `json:"error,omitempty"`
	Notifier  *ecsLogNotifier `json:"notifier,omitempty"`
}

type ecsLogLog struct {
	Level string `json:"level"`
}

type ecsLogProcess struct {
	PID    int            `json:"pid"`
	Thread ecsLogThread   `json:"thread"`
}

type ecsLogThread struct {
	Name string `json:"name"`
}

type ecsLogService struct {
	Name string `json:"name"`
}

type ecsLogEcs struct {
	Version string `json:"version"`
}

type ecsLogError struct {
	Message string `json:"message"`
}

type ecsLogNotifier struct {
	Subject  string   `json:"subject,omitempty"`
	To       []string `json:"to,omitempty"`
	Provider string   `json:"provider,omitempty"`
}

type ecsLogTrace struct {
	ID string `json:"id"`
}

var ecsBase = ecsLog{
	Log:     ecsLogLog{},
	Process: ecsLogProcess{PID: os.Getpid(), Thread: ecsLogThread{Name: "main"}},
	Service: ecsLogService{Name: "notifier"},
	Ecs:     ecsLogEcs{Version: "1.6.0"},
}

func logECS(level, message, traceID string, extra ecsLog) {
	entry := ecsBase
	entry.Timestamp = time.Now().UTC().Format(time.RFC3339Nano)
	entry.Log.Level = level
	entry.Message = message
	entry.Trace = &ecsLogTrace{ID: traceID}
	entry.Error = extra.Error
	entry.Notifier = extra.Notifier
	json.NewEncoder(os.Stdout).Encode(entry)
}

func logInfo(message, traceID string, n *ecsLogNotifier) {
	logECS("INFO", message, traceID, ecsLog{Notifier: n})
}

func logWarn(message, traceID, errMsg string, n *ecsLogNotifier) {
	logECS("WARN", message, traceID, ecsLog{Error: &ecsLogError{Message: errMsg}, Notifier: n})
}

func logError(message, traceID, errMsg string, n *ecsLogNotifier) {
	logECS("ERROR", message, traceID, ecsLog{Error: &ecsLogError{Message: errMsg}, Notifier: n})
}

type sendRequest struct {
	To          []string     `json:"to"`
	Subject     string       `json:"subject"`
	Body        string       `json:"body"`
	ContentType string       `json:"contentType"`
	Attachments []attachment `json:"attachments"`
}

type attachment struct {
	Filename      string `json:"filename"`
	ContentBase64 string `json:"contentBase64"`
}

type decodedAttachment struct {
	filename    string
	contentType string
	data        []byte
}

const (
	maxRequestBytes         = 20 << 20
	maxAttachmentBytes      = 10 << 20
	maxTotalAttachmentBytes = 15 << 20
	maxAttachments          = 5
)

func decodeAttachments(raw []attachment) ([]decodedAttachment, error) {
	if len(raw) == 0 {
		return nil, nil
	}
	if len(raw) > maxAttachments {
		return nil, fmt.Errorf("too many attachments")
	}
	result := make([]decodedAttachment, 0, len(raw))
	total := 0
	for _, item := range raw {
		filename := filepath.Base(strings.TrimSpace(item.Filename))
		if filename == "" || filename == "." || filename == ".." || strings.ContainsAny(filename, "\r\n\"") {
			return nil, fmt.Errorf("invalid attachment filename")
		}
		data, err := base64.StdEncoding.DecodeString(item.ContentBase64)
		if err != nil {
			return nil, fmt.Errorf("invalid attachment content")
		}
		total += len(data)
		if len(data) == 0 || len(data) > maxAttachmentBytes || total > maxTotalAttachmentBytes {
			return nil, fmt.Errorf("attachment too large")
		}
		contentType := mime.TypeByExtension(strings.ToLower(filepath.Ext(filename)))
		if contentType == "" {
			contentType = "application/octet-stream"
		}
		result = append(result, decodedAttachment{filename: filename, contentType: contentType, data: data})
	}
	return result, nil
}

type resendError struct {
	message string
	code    int
}

func (e *resendError) Error() string {
	return e.message
}

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func newTraceID() string {
	// UUID v4 dari crypto/rand (tanpa dependency).
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return ""
	}
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}

func resolveTraceID(r *http.Request) string {
	if id := strings.TrimSpace(r.Header.Get("X-Trace-Id")); id != "" {
		return id
	}
	return newTraceID()
}

func main() {
	provider := env("MAIL_PROVIDER", "smtp")
	notifierToken := strings.TrimSpace(os.Getenv("NOTIFIER_TOKEN"))
	if notifierToken == "" {
		logWarn("notifier token not configured; /send will reject requests", "", "NOTIFIER_TOKEN empty", nil)
	}

	http.HandleFunc("/health", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})

	http.HandleFunc("/send", func(w http.ResponseWriter, r *http.Request) {
		traceID := resolveTraceID(r)
		w.Header().Set("X-Trace-Id", traceID)
		if r.Method != http.MethodPost {
			logWarn("send validation error", traceID, "method not allowed", nil)
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		if notifierToken == "" {
			logError("send rejected", traceID, "notifier token not configured", nil)
			http.Error(w, "notifier token not configured", http.StatusServiceUnavailable)
			return
		}
		providedToken := strings.TrimSpace(r.Header.Get("X-Notifier-Token"))
		if subtle.ConstantTimeCompare([]byte(providedToken), []byte(notifierToken)) != 1 {
			logWarn("send rejected", traceID, "invalid notifier token", nil)
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}

		r.Body = http.MaxBytesReader(w, r.Body, maxRequestBytes)
		var req sendRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			logWarn("send validation error", traceID, err.Error(), nil)
			http.Error(w, "bad request", http.StatusBadRequest)
			return
		}
		if len(req.To) == 0 || req.Subject == "" || req.Body == "" {
			logWarn("send validation error", traceID, "to, subject, body required", nil)
			http.Error(w, "to, subject, body required", http.StatusBadRequest)
			return
		}
		req.Subject = sanitizeHeader(req.Subject)
		if req.Subject == "" {
			logWarn("send validation error", traceID, "invalid subject", nil)
			http.Error(w, "invalid subject", http.StatusBadRequest)
			return
		}
		for i, recipient := range req.To {
			address, err := mail.ParseAddress(strings.TrimSpace(recipient))
			if err != nil || strings.ContainsAny(recipient, "\r\n") {
				logWarn("send validation error", traceID, "invalid recipient", nil)
				http.Error(w, "invalid recipient", http.StatusBadRequest)
				return
			}
			req.To[i] = address.Address
		}
		attachments, err := decodeAttachments(req.Attachments)
		if err != nil {
			logWarn("send validation error", traceID, err.Error(), nil)
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}

		n := &ecsLogNotifier{Subject: req.Subject, To: req.To, Provider: provider}
		logInfo("send request", traceID, n)

		// Urutan provider: primer = MAIL_PROVIDER; jika gagal dan RESEND_FALLBACK=true,
		// coba Resend (asalkan terkonfigurasi).
		switch provider {
		case "resend":
			err = resendSend(req, attachments)
		default: // smtp
			err = smtpSend(req, attachments)
			if err != nil && env("RESEND_FALLBACK", "false") == "true" {
				err = resendSend(req, attachments)
			}
		}

		if err != nil {
			code := http.StatusBadGateway
			if re, ok := err.(*resendError); ok {
				code = re.code
			}
			logError("send failed", traceID, err.Error(), n)
			http.Error(w, err.Error(), code)
			return
		}

		logInfo("send success", traceID, n)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"success":true}`))
	})

	listen := ":" + env("PORT", "8081")
	logInfo("notifier started", "", &ecsLogNotifier{Provider: provider})
	server := &http.Server{
		Addr:              listen,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	if err := server.ListenAndServe(); err != nil {
		fmt.Fprintln(os.Stderr, "server error:", err)
		os.Exit(1)
	}
}

func sanitizeHeader(value string) string {
	value = strings.ReplaceAll(value, "\r", " ")
	value = strings.ReplaceAll(value, "\n", " ")
	return strings.TrimSpace(value)
}

func smtpSend(req sendRequest, attachments []decodedAttachment) error {
	host := env("SMTP_HOST", "smtp.gmail.com")
	addr := host + ":" + env("SMTP_PORT", "587")
	user := os.Getenv("SMTP_USER")
	pass := os.Getenv("SMTP_APP_PASSWORD")
	from := env("SMTP_FROM", user)

	ct := req.ContentType
	if ct == "" {
		ct = "text/html"
	}

	var msg bytes.Buffer
	msg.WriteString("From: " + from + "\r\n")
	msg.WriteString("To: " + strings.Join(req.To, ", ") + "\r\n")
	msg.WriteString("Subject: " + req.Subject + "\r\n")
	msg.WriteString("MIME-Version: 1.0\r\n")

	if len(attachments) == 0 {
		msg.WriteString("Content-Type: " + ct + "; charset=UTF-8\r\n\r\n")
		msg.WriteString(req.Body)
	} else {
		mw := multipart.NewWriter(&msg)
		msg.WriteString("Content-Type: multipart/mixed; boundary=\"" + mw.Boundary() + "\"\r\n\r\n")

		htmlHeader := textproto.MIMEHeader{}
		htmlHeader.Set("Content-Type", ct+"; charset=UTF-8")
		htmlHeader.Set("Content-Transfer-Encoding", "base64")
		part, err := mw.CreatePart(htmlHeader)
		if err != nil {
			return err
		}
		if err := writeBase64Lines(part, []byte(req.Body)); err != nil {
			return err
		}

		for _, item := range attachments {
			header := textproto.MIMEHeader{}
			header.Set("Content-Type", item.contentType+"; name=\""+item.filename+"\"")
			header.Set("Content-Disposition", "attachment; filename=\""+item.filename+"\"")
			header.Set("Content-Transfer-Encoding", "base64")
			part, err := mw.CreatePart(header)
			if err != nil {
				return err
			}
			if err := writeBase64Lines(part, item.data); err != nil {
				return err
			}
		}
		if err := mw.Close(); err != nil {
			return err
		}
	}

	auth := smtp.PlainAuth("", user, pass, host)
	return smtp.SendMail(addr, auth, from, req.To, msg.Bytes())
}

func writeBase64Lines(w io.Writer, data []byte) error {
	encoded := base64.StdEncoding.EncodeToString(data)
	for len(encoded) > 76 {
		if _, err := io.WriteString(w, encoded[:76]+"\r\n"); err != nil {
			return err
		}
		encoded = encoded[76:]
	}
	_, err := io.WriteString(w, encoded+"\r\n")
	return err
}

func resendSend(req sendRequest, attachments []decodedAttachment) error {
	key := os.Getenv("RESEND_API_KEY")
	from := os.Getenv("RESEND_FROM")
	if key == "" || from == "" {
		return &resendError{message: "resend not configured (RESEND_API_KEY/RESEND_FROM)", code: http.StatusInternalServerError}
	}

	ct := req.ContentType
	if ct == "" {
		ct = "text/html"
	}
	field := "html"
	if strings.Contains(ct, "text/plain") {
		field = "text"
	}
	payload := map[string]any{
		"from":    from,
		"to":      req.To,
		"subject": req.Subject,
		field:     req.Body,
	}
	if len(attachments) > 0 {
		list := make([]map[string]string, 0, len(attachments))
		for _, item := range attachments {
			list = append(list, map[string]string{
				"filename": item.filename,
				"content":  base64.StdEncoding.EncodeToString(item.data),
			})
		}
		payload["attachments"] = list
	}
	body, err := json.Marshal(payload)
	if err != nil {
		return err
	}

	httpReq, err := http.NewRequest("POST", "https://api.resend.com/emails", bytes.NewReader(body))
	if err != nil {
		return err
	}
	httpReq.Header.Set("Authorization", "Bearer "+key)
	httpReq.Header.Set("Content-Type", "application/json")

	client := &http.Client{Timeout: 15 * time.Second}
	resp, err := client.Do(httpReq)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusTooManyRequests {
		// Limit tercapai: lewati (jangan kirim), laporkan sebagai 429.
		return &resendError{message: "resend rate limited, skipped", code: http.StatusTooManyRequests}
	}
	if resp.StatusCode >= 300 {
		return &resendError{message: fmt.Sprintf("resend status %d", resp.StatusCode), code: resp.StatusCode}
	}
	return nil
}
