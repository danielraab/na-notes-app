// Command server runs the na-notes-app Go backend.
package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/danielraab/na-notes-app/backend-go/internal/auth"
	"github.com/danielraab/na-notes-app/backend-go/internal/config"
	"github.com/danielraab/na-notes-app/backend-go/internal/db"
	"github.com/danielraab/na-notes-app/backend-go/internal/httpapi"
	"github.com/danielraab/na-notes-app/backend-go/internal/mail"
	"github.com/danielraab/na-notes-app/backend-go/internal/notes"
	"github.com/danielraab/na-notes-app/backend-go/internal/users"
)

func main() {
	if err := run(); err != nil {
		slog.Error("server exited", "error", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	sqlDB, err := db.Open(cfg.DatabasePath)
	if err != nil {
		return err
	}
	defer sqlDB.Close()

	discoveryCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	oidcClient, err := auth.NewOIDC(discoveryCtx, cfg.OIDCIssuerURL, cfg.OIDCClientID, cfg.OIDCClientSecret, cfg.OIDCRedirectURL, cfg.OIDCScopes)
	if err != nil {
		return err
	}

	usersRepo := users.NewRepository(sqlDB)
	notesRepo := notes.NewRepository(sqlDB)
	mailer := mail.New(cfg.SMTPHost, cfg.SMTPPort, cfg.SMTPUsername, cfg.SMTPPassword, cfg.SMTPFrom)
	notesService := notes.NewService(notesRepo, usersRepo, mailer, cfg.FrontendURL)
	authStore := auth.NewStore(sqlDB)

	handler := httpapi.NewRouter(&httpapi.Deps{
		Config:    cfg,
		AuthStore: authStore,
		OIDC:      oidcClient,
		Users:     usersRepo,
		Notes:     notesService,
	})

	srv := &http.Server{
		Addr:              cfg.ListenAddr,
		Handler:           handler,
		ReadHeaderTimeout: 10 * time.Second,
	}

	slog.Info("listening", "addr", cfg.ListenAddr)
	return srv.ListenAndServe()
}
