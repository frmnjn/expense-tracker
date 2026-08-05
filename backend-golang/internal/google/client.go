package google

import (
	"context"
	"fmt"
	"log/slog"

	"google.golang.org/api/option"
	"google.golang.org/api/sheets/v4"
)

const sheetName = "Expenses"

type GoogleSheetsClient struct {
	svc           *sheets.Service
	spreadsheetID string
}

func NewGoogleSheetsClient(ctx context.Context, credentialsPath, spreadsheetID string) (*GoogleSheetsClient, error) {
	svc, err := sheets.NewService(ctx, option.WithCredentialsFile(credentialsPath))
	if err != nil {
		return nil, fmt.Errorf("failed to create google sheets client: %w", err)
	}
	return &GoogleSheetsClient{svc: svc, spreadsheetID: spreadsheetID}, nil
}

func (c *GoogleSheetsClient) Ping(ctx context.Context) error {
	spreadsheet, err := c.svc.Spreadsheets.Get(c.spreadsheetID).Context(ctx).Do()
	if err != nil {
		return fmt.Errorf("failed to access spreadsheet: %w", err)
	}
	titles := make([]string, 0, len(spreadsheet.Sheets))
	for _, sheet := range spreadsheet.Sheets {
		titles = append(titles, sheet.Properties.Title)
		if sheet.Properties.Title == sheetName {
			return nil
		}
	}
	return fmt.Errorf("sheet %q not found in spreadsheet, available sheets: %v", sheetName, titles)
}

func (c *GoogleSheetsClient) AppendExpense(ctx context.Context, date, description string, amount int64) error {
	valueRange := fmt.Sprintf("%s!A:C", sheetName)
	_, err := c.svc.Spreadsheets.Values.Append(c.spreadsheetID, valueRange, &sheets.ValueRange{
		Values: [][]interface{}{{date, description, amount}},
	}).
		ValueInputOption("RAW").
		InsertDataOption("INSERT_ROWS").
		Context(ctx).
		Do()
	if err != nil {
		slog.Error("error from google sheets api", "error", err)
		return fmt.Errorf("failed to append expense to google sheets: %w", err)
	}
	return nil
}
