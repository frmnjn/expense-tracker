//go:build integration

package google

import (
	"context"
	"os"
	"testing"
)

func TestConnection(t *testing.T) {
	credentialsPath := os.Getenv("GOOGLE_APPLICATION_CREDENTIALS")
	spreadsheetID := os.Getenv("GOOGLE_SHEET_ID")
	if credentialsPath == "" || spreadsheetID == "" {
		t.Skip("google credentials not configured")
	}

	client, err := NewGoogleSheetsClient(context.Background(), credentialsPath, spreadsheetID)
	if err != nil {
		t.Fatalf("failed to create client: %v", err)
	}

	if err := client.Ping(context.Background()); err != nil {
		t.Fatalf("connection test failed: %v", err)
	}
}

func TestAppendExpense(t *testing.T) {
	credentialsPath := os.Getenv("GOOGLE_APPLICATION_CREDENTIALS")
	spreadsheetID := os.Getenv("GOOGLE_SHEET_ID")
	if credentialsPath == "" || spreadsheetID == "" {
		t.Skip("google credentials not configured")
	}

	client, err := NewGoogleSheetsClient(context.Background(), credentialsPath, spreadsheetID)
	if err != nil {
		t.Fatalf("failed to create client: %v", err)
	}

	if err := client.AppendExpense(context.Background(), "2026-08-06", "Integration test", 1); err != nil {
		t.Fatalf("append failed: %v", err)
	}
}
