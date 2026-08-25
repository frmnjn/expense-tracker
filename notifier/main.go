package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/smtp"
	"os"
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

var ecsBase = ecsLog{
	Log:     ecsLogLog{},
	Process: ecsLogProcess{PID: os.Getpid(), Thread: ecsLogThread{Name: "main"}},
	Service: ecsLogService{Name: "notifier"},
	Ecs:     ecsLogEcs{Version: "1.6.0"},
}

func logECS(level, message string, extra ecsLog) {
	entry := ecsBase
	entry.Timestamp = time.Now().UTC().Format(time.RFC3339Nano)
	entry.Log.Level = level
	entry.Message = message
	entry.Error = extra.Error
	entry.Notifier = extra.Notifier
	json.NewEncoder(os.Stdout).Encode(entry)
}

func logInfo(message string, n *ecsLogNotifier) {
	logECS("INFO", message, ecsLog{Notifier: n})
}

func logWarn(message, errMsg string, n *ecsLogNotifier) {
	logECS("WARN", message, ecsLog{Error: &ecsLogError{Message: errMsg}, Notifier: n})
}

func logError(message, errMsg string, n *ecsLogNotifier) {
	logECS("ERROR", message, ecsLog{Error: &ecsLogError{Message: errMsg}, Notifier: n})
}

type sendRequest struct {
	To          []string `json:"to"`
	Subject     string   `json:"subject"`
	Body        string   `json:"body"`
	ContentType string   `json:"contentType"`
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

func main() {
	provider := env("MAIL_PROVIDER", "smtp")

	http.HandleFunc("/health", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})

	http.HandleFunc("/send", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			logWarn("send validation error", "method not allowed", nil)
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		var req sendRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			logWarn("send validation error", err.Error(), nil)
			http.Error(w, "bad request", http.StatusBadRequest)
			return
		}
		if len(req.To) == 0 || req.Subject == "" || req.Body == "" {
			logWarn("send validation error", "to, subject, body required", nil)
			http.Error(w, "to, subject, body required", http.StatusBadRequest)
			return
		}

		n := &ecsLogNotifier{Subject: req.Subject, To: req.To, Provider: provider}
		logInfo("send request", n)

		var err error
		// Urutan provider: primer = MAIL_PROVIDER; jika gagal dan RESEND_FALLBACK=true,
		// coba Resend (asalkan terkonfigurasi).
		switch provider {
		case "resend":
			err = resendSend(req)
		default: // smtp
			err = smtpSend(req)
			if err != nil && env("RESEND_FALLBACK", "false") == "true" {
				err = resendSend(req)
			}
		}

		if err != nil {
			code := http.StatusBadGateway
			if re, ok := err.(*resendError); ok {
				code = re.code
			}
			logError("send failed", err.Error(), n)
			http.Error(w, err.Error(), code)
			return
		}

		logInfo("send success", n)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"success":true}`))
	})

	listen := ":" + env("PORT", "8081")
	logInfo("notifier started", &ecsLogNotifier{Provider: provider})
	fmt.Println("notifier listening on", listen, "provider:", provider)
	if err := http.ListenAndServe(listen, nil); err != nil {
		fmt.Fprintln(os.Stderr, "server error:", err)
		os.Exit(1)
	}
}

func smtpSend(req sendRequest) error {
	host := env("SMTP_HOST", "smtp.gmail.com")
	addr := host + ":" + env("SMTP_PORT", "587")
	user := os.Getenv("SMTP_USER")
	pass := os.Getenv("SMTP_APP_PASSWORD")
	from := env("SMTP_FROM", user)

	ct := req.ContentType
	if ct == "" {
		ct = "text/html"
	}
	msg := "From: " + from + "\r\n" +
		"To: " + strings.Join(req.To, ", ") + "\r\n" +
		"Subject: " + req.Subject + "\r\n" +
		"MIME-Version: 1.0\r\n" +
		"Content-Type: " + ct + "; charset=UTF-8\r\n" +
		"\r\n" +
		req.Body

	auth := smtp.PlainAuth("", user, pass, host)
	return smtp.SendMail(addr, auth, from, req.To, []byte(msg))
}

func resendSend(req sendRequest) error {
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
