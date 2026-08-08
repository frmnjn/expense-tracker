package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/smtp"
	"os"
	"strings"
)

type sendRequest struct {
	To          []string `json:"to"`
	Subject     string   `json:"subject"`
	Body        string   `json:"body"`
	ContentType string   `json:"contentType"`
}

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func main() {
	host := env("SMTP_HOST", "smtp.gmail.com")
	addr := host + ":" + env("SMTP_PORT", "587")
	user := os.Getenv("SMTP_USER")
	pass := os.Getenv("SMTP_APP_PASSWORD")
	from := env("SMTP_FROM", user)

	http.HandleFunc("/health", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})

	http.HandleFunc("/send", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		var req sendRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "bad request", http.StatusBadRequest)
			return
		}
		if len(req.To) == 0 || req.Subject == "" || req.Body == "" {
			http.Error(w, "to, subject, body required", http.StatusBadRequest)
			return
		}

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
		if err := smtp.SendMail(addr, auth, from, req.To, []byte(msg)); err != nil {
			http.Error(w, "smtp error: "+err.Error(), http.StatusBadGateway)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"success":true}`))
	})

	listen := ":" + env("PORT", "8081")
	fmt.Println("notifier listening on", listen)
	if err := http.ListenAndServe(listen, nil); err != nil {
		fmt.Fprintln(os.Stderr, "server error:", err)
		os.Exit(1)
	}
}
