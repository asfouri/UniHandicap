# Security Policy

## Supported Status

This is a portfolio project. Security issues are reviewed as part of continuous improvement and production-readiness hardening.

## Reporting a Vulnerability

Please do not open public issues for sensitive security problems.

If you discover a vulnerability, contact the maintainer privately with:

- A clear description of the issue
- Steps to reproduce
- Affected files or modules
- Potential impact
- Suggested fix, if available

## Security Priorities

This project prioritizes:

- Safe database access
- Input validation
- Secure handling of credentials
- Least-privilege access controls
- Avoiding hardcoded secrets
- Clear documentation for secure setup

## Production Hardening Checklist

Before production use:

- Move all credentials to environment variables
- Enable dependency scanning
- Add automated tests for authorization-sensitive flows
- Review file upload and export paths
- Enable CI checks on every pull request
