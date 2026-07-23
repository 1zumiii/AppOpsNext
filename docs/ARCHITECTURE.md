# Architecture

The project is split by responsibility so Android-version and OEM-specific
behavior stays isolated from UI code.

## Package boundaries

- `model`: immutable app-wide data models.
- `presentation`: Compose screens, reusable components, and screen state.
- `shizuku`: binder lifecycle, authorization, and UserService connection.
- `appops`: AppOps commands, parsing, mode mapping, and privileged adapters.
- `apps`: installed-application discovery and metadata.
- `settings`: Preferences DataStore and typed user settings.
- `data`: persistence for templates and backups as those modules land.

The `shizuku` package owns privileged-process lifecycle only. The `appops`
package owns command construction, execution results, parsing, and repository
state. The `apps` package owns installed-application discovery and pure search
filtering. Persistence packages are introduced as their feature modules land.

`AppOpsNextApplication` owns the single `PrivilegedServiceClient` and
`UserSettingsRepository` instances. The privileged client is shared by
diagnostics and per-app detail ViewModels; feature ViewModels consume the shared
state and repository gateway rather than starting competing privileged
services. The settings repository owns the only `user_settings` DataStore
instance.

The separate `test-target` application is disposable and contains no user
data. Privileged write development must target this package until production
write safeguards and confirmation UI are complete.

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

## Safe write proof

The temporary debug-only write card runs a bounded transaction against
`dev.izumi.appopsnext.testtarget`:

```text
read original package mode
    -> set typed test mode
    -> read and verify test mode
    -> restore original mode
    -> read and verify restored mode
```

Once the original value has been read, every later failure path attempts
restoration. Failure state distinguishes “no write occurred,” “restored,” and
“restore could not be confirmed.” The confirmed package-mode editor below
preserves the same contract; the temporary card remains only as a development
diagnostic until its removal milestone is reached.

## Application discovery

The app list uses the regular current-user `PackageManager` API and declares
`QUERY_ALL_PACKAGES` because an AppOps manager cannot know target package names
at build time. The manifest suppresses only the dedicated lint policy warning
and records the reason inline; no general lint baseline is used.

`InstalledAppsRepository` performs package and label loading off the main
thread. Search is a pure function in `AppListFilter`, which keeps filtering
testable without Android framework mocks. Selecting a row loads a structured
`PackageOpsSnapshot`. `AppOpDisplayCatalog` then groups duplicate package/UID
entries, attaches localized resource identifiers, applies a common-first
priority, and supports matching the current label, English label, or raw system
operation name. The UI displays exactly one current-locale title and keeps the
raw operation name on a separate lower-emphasis line. Raw shell timing metadata
stays in the snapshot, while the UI formatter rounds it to seconds and displays
at most the two largest non-zero time units.

The hide-system-apps preference is persisted with Preferences DataStore and
combined with application search inside `AppListViewModel`. Filtering itself
remains a pure function.

## Confirmed package and UID writes

Full `cmd appops get <PACKAGE>` output can contain a multi-line UID block where
only its first row has the `Uid mode:` prefix. The repository therefore uses
single-operation reads to resolve the scope of every discovered operation
before exposing it for editing. When both scopes exist, the effective UID entry
is preferred in the display catalog.

A requested change moves through a dedicated ViewModel state machine:

```text
select typed mode
    -> explicit user confirmation
    -> re-read and compare the original mode
    -> write requested mode
    -> read and verify requested mode
```

If the original mode changed after the screen loaded, the transaction stops
without writing. If the write or requested-mode verification fails, the
repository restores the original value and verifies the restoration before it
reports a result. UI code receives typed phases and restoration status rather
than parsing command output.

Package writes use `cmd appops set <PACKAGE> <OP> <MODE>`. UID writes use the
explicit `--uid` variant and show every package returned for that UID before
confirmation. Android may normalize or reject a requested UID mode when it is
coupled to a runtime permission; this is reported as a verification failure,
and the repository restores and verifies the original mode.

## Maintenance rules

1. User-visible text belongs in Android string resources.
2. AppOp names are persisted as strings; numeric op codes are runtime-only.
3. Shell command construction is centralized and arguments are never assembled
   from unvalidated UI strings.
4. Android-version and OEM differences stay behind adapter interfaces.
5. Parsers and mode mappings require unit tests with captured, anonymized
   fixtures.
6. A write operation must expose the old value, verify the result, and restore
   the original value after a failed change.
7. Placeholder or temporary UI must include visible status text and a searchable
   `TODO` explaining its removal condition.
