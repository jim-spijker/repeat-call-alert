# Security

## Reporting a vulnerability

Please report security issues privately via
[GitHub private vulnerability reporting](https://github.com/jim-spijker/repeat-call-alert/security/advisories/new)
rather than in a public issue.

## Design notes

- **No internet permission.** The app cannot send data anywhere.
- **Never blocks calls.** The call screening service always answers "allow", within a 3.5 s budget (Android allows 5 s).
- **Minimal stored data.** Numbers are stored only as SHA-256 hashes with a random per-install salt, plus timestamps,
  and deleted after 35 minutes. The activity log shows at most the last 3 digits of a number.
- **No backups.** `allowBackup=false` and data extraction rules exclude all app data from cloud backup and device transfer.
- **Exported components** only accept system-protected inputs. The screening service requires `BIND_SCREENING_SERVICE`.
  The receivers listen only for the protected `PHONE_STATE` and `BOOT_COMPLETED` broadcasts. The alert service is not exported.
- **Release builds** don't write to logcat and are minified with R8.
