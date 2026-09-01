# Security Policy

## Supported Versions

Only the latest released version is supported. Security fixes are not backported to older tags.

| Version  | Supported          |
| -------- | ------------------ |
| latest   | :white_check_mark: |
| < latest | :x:                |

## Reporting a Vulnerability

Please follow these steps if you discover a security vulnerability in this project:

### Do Not

- **Do not** open a public GitHub issue for security vulnerabilities
- **Do not** disclose the vulnerability publicly until it has been addressed

### Do

1. **Report privately** via [GitHub Security Advisories](https://github.com/miikkak/online-players/security/advisories/new) <!-- markdownlint-disable-line MD013 -->
2. **Include in your report:**
   - Description of the vulnerability
   - Steps to reproduce the issue
   - Potential impact
   - Suggested fix (if you have one)

3. **Response timeline:**
   - You should receive an acknowledgment within 48 hours
   - We'll provide a detailed response within 7 days
   - We'll work with you to understand and fix the issue
   - We'll release a fix as soon as possible

## Scope

This plugin writes network-wide and per-server online player counts to a JSON file on the local
filesystem. It doesn't open any network listeners of its own, doesn't read credentials, and
doesn't execute anything derived from untrusted input - its attack surface is limited to whatever
Velocity itself exposes to plugins. If you find a way this plugin could be used to affect the
proxy or other plugins beyond its documented behavior, that's exactly the kind of thing to
report.

## Security Best Practices

When using this plugin in production:

- Always use a specific released version, not a locally built `SNAPSHOT` jar, in production
- The output file is deliberately world-readable by design (so a status page or monitoring
  script can read it) - only put this plugin on a proxy where that data (player counts) is fine
  to expose to anything with local filesystem access
- Keep the plugin updated - check releases periodically or watch the repository

## Security Scanning

This project uses automated security scanning:

- **Trivy** (filesystem scan against `gradle.lockfile`) for dependency vulnerability scanning,
  on a weekly schedule and on demand
- **Renovate** for automated dependency updates
- AI code review on every pull request

## Disclosure Policy

- Security issues are fixed in private before public disclosure
- After a fix is released, we publish a security advisory
- We credit reporters in the advisory (unless they prefer anonymity)

## Past Security Advisories

No security advisories have been published yet.

## Contact

For security-related questions or concerns, please use the reporting method above rather than
public channels.
