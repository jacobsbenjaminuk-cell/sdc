# Bugs

## Interface-operation mutations lacked component edit permission checks

Found by: Devin security scan, finding `sfind-5132ae4a8ee4400787d485fd5d12a907`.

The instance create/update and resource create endpoints checked the caller's role,
but not whether the target component was editable by that caller. They could alter
certified, archived or deleted components, or components checked out by another user.

Pass the caller's user ID to the business logic and validate the component before
inspecting or changing its interfaces. Let component permission exceptions reach
the existing REST exception mapper.

Regression tests cover all three mutations and confirm permission failures occur
before component mutation, locking or persistence. The 15 denial cases failed
before adding the checks.
