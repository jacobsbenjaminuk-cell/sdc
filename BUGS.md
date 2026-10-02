# Bugs

## User directory reads lacked authorisation

Found by: Devin security scan, finding `sfind-ad9da894e1164dffb459ad455278fced`.

The user details, role, administrator list and user list endpoints did not check
the caller's permission to read those records. Active administrators may read
the directory; other active users may read only their own details and role.
Missing or blank caller IDs are rejected before any user lookup.

Regression coverage: `UserAdminServletTest` exercises these endpoints over HTTP,
including filtered user lists, every supported role and inactive callers.
