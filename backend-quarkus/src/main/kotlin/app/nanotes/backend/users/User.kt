package app.nanotes.backend.users

import java.time.Instant

data class User(val id: String, val email: String, val displayName: String, val avatarUrl: String?, val createdAt: Instant)
