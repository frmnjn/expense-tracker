package config

import (
	"os"

	"github.com/joho/godotenv"
)

type Config struct {
	Port                             string
	GoogleSheetID                    string
	GoogleApplicationCredentialsPath string
}

func Load() Config {
	_ = godotenv.Load()

	return Config{
		Port:                             getEnv("PORT", "8080"),
		GoogleSheetID:                    os.Getenv("GOOGLE_SHEET_ID"),
		GoogleApplicationCredentialsPath: os.Getenv("GOOGLE_APPLICATION_CREDENTIALS"),
	}
}

func getEnv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
