// Package apperr defines the small set of sentinel errors the HTTP layer
// maps to status codes, so domain packages stay free of HTTP concerns.
package apperr

import "errors"

var (
	ErrNotFound        = errors.New("not found")
	ErrForbidden       = errors.New("forbidden")
	ErrVersionConflict = errors.New("version conflict")
	ErrValidation      = errors.New("validation failed")
)
