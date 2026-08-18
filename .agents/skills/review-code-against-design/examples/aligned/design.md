# Notification Dispatcher — Design

## Purpose
Send notifications to users via email or SMS. Channel is determined by user preference.

## Modules
- `NotificationService` — public entry point; accepts a notification request and delegates to the correct sender
- `EmailSender` — sends email via SMTP; retries up to 3 times on transient failure
- `SmsSender` — sends SMS via Twilio; retries up to 3 times on transient failure

## Interfaces
```
NotificationService.send(user_id: str, message: str, subject: str | None) -> Result
```

## State / Flow
1. Look up user preference (email | sms)
2. Validate: message must be non-empty; user_id must be resolvable
3. Delegate to EmailSender or SmsSender
4. On permanent failure, log the failure with user_id, channel, and error code
5. Return Result(success: bool, channel: str, error: str | None)

## Error Handling
- Transient errors: retry up to 3 times with 1s backoff
- Permanent errors: log and return failure result; do not raise exceptions to the caller
- Unknown user: return failure result immediately without retrying

## Integration
- User preference looked up from UserPreferenceStore (injected dependency)
- SMTP credentials from environment variable `SMTP_PASSWORD`
- Twilio credentials from environment variables `TWILIO_SID` and `TWILIO_TOKEN`
