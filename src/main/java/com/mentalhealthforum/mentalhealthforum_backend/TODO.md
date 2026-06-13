# TODO - Activity Tracking Feature

## Description
Implement user activity tracking to populate `lastActiveAt` field in user profiles.

## Requirements
- Track user activity (logins, API calls, forum posts)
- Update `app_users.last_active_at` timestamp on activity
- Decide on update frequency (every request? every 5 minutes?)
- Consider performance implications

## Affected Features
- User Connections (shows last active status)
- User Profiles
- Future discovery features

## Implementation Ideas
1. Add filter/interceptor to update timestamp on authenticated requests
2. Use reactive approach with `Mono.defer()` to avoid blocking
3. Consider rate limiting updates (e.g., update only if last update > 5 minutes ago)

## Priority
Low - Nice to have, not blocking MVP

## Estimated Effort
2-3 days

## Dependencies
None