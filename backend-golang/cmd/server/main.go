package main

import (
	"log/slog"
	"os"

	"expense-tracker-backend/internal/config"
	"expense-tracker-backend/internal/routes"
)

func main() {
	cfg := config.Load()

	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	slog.SetDefault(logger)

	router := routes.SetupRouter()

	slog.Info("server started", "port", cfg.Port)
	if err := router.Run(":" + cfg.Port); err != nil {
		slog.Error("server failed to start", "error", err)
		os.Exit(1)
	}
}
