# Architecture

The project is split by responsibility so Android-version and OEM-specific
behavior stays isolated from UI code.

## Package boundaries

- `model`: immutable app-wide data models.
- `presentation`: Compose screens, reusable components, and screen state.
- `shizuku`: binder lifecycle, authorization, and UserService connection.
- `appops`: AppOps commands, parsing, mode mapping, and privileged adapters.
- `apps`: installed-application discovery and metadata.
- `data`: persistence for templates, backups, and settings.

The `shizuku` package owns privileged-process lifecycle only. The `appops`
package owns command construction, execution results, parsing, and repository
state. App discovery and persistence packages are introduced as their feature
modules land.

## Privileged read path

```text
HomeViewModel
    -> AppOpsRepository
    -> PrivilegedServiceClient
    -> IPrivilegedAppOpsService
    -> AppOpsUserService (shell UID)
    -> CommandExecutor
    -> /system/bin/cmd appops
```

Only validated argument lists cross into `ProcessBuilder`; commands are never
constructed as shell strings. The AIDL boundary returns a typed
`ShellCommandResult`, and parsing remains in the regular app process so it can
be unit tested without Shizuku or a device.

## Maintenance rules

1. User-visible text belongs in Android string resources.
2. AppOp names are persisted as strings; numeric op codes are runtime-only.
3. Shell command construction is centralized and arguments are never assembled
   from unvalidated UI strings.
4. Android-version and OEM differences stay behind adapter interfaces.
5. Parsers and mode mappings require unit tests with captured, anonymized
   fixtures.
6. A write operation must expose the old value and support explicit restoration.
