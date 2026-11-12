package main

import (
	"bufio"
	"bytes"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

const (
	shizukuServerName = "shizuku_server"
	nativeDaemonName  = "appopsnextd"
)

// relevantProcess matches the app's own processes, this daemon and the Shizuku
// server, the same rule as the app's ProcessStats.isRelevant. Nothing else on
// the device is ever listed.
func relevantProcess(name string, packageName string) bool {
	return name == packageName ||
		strings.HasPrefix(name, packageName+":") ||
		name == shizukuServerName ||
		filepath.Base(name) == nativeDaemonName
}

// collectProcesses lists the relevant processes under procRoot, one per line:
// UID, name and the stat line, separated by tabs, sorted like the app sorts
// them before it parses the stat lines.
func collectProcesses(procRoot string, packageName string) commandResult {
	entries, err := os.ReadDir(procRoot)
	if err != nil {
		return commandResult{exitCode: startFailureExitCode, stderr: []byte(err.Error())}
	}
	var lines []string
	for _, entry := range entries {
		if _, err := strconv.Atoi(entry.Name()); err != nil {
			continue
		}
		directory := filepath.Join(procRoot, entry.Name())
		// A process can exit between the listing and the reads.
		cmdline, err := os.ReadFile(filepath.Join(directory, "cmdline"))
		if err != nil {
			continue
		}
		name := commandName(cmdline)
		if name == "" || !relevantProcess(name, packageName) {
			continue
		}
		stat, err := os.ReadFile(filepath.Join(directory, "stat"))
		if err != nil {
			continue
		}
		uid, ok := processUID(filepath.Join(directory, "status"))
		if !ok {
			continue
		}
		lines = append(lines, strings.Join([]string{
			strconv.Itoa(uid),
			clean(name),
			clean(strings.TrimSpace(string(stat))),
		}, "\t"))
	}
	sort.Strings(lines)
	return commandResult{exitCode: 0, stdout: []byte(strings.Join(lines, "\n"))}
}

// commandName is the first argument of a cmdline file, which is NUL padded.
func commandName(cmdline []byte) string {
	if end := bytes.IndexByte(cmdline, 0); end >= 0 {
		cmdline = cmdline[:end]
	}
	return string(cmdline)
}

// processUID reads the real UID from the Uid line of a status file.
func processUID(statusPath string) (int, bool) {
	file, err := os.Open(statusPath)
	if err != nil {
		return 0, false
	}
	defer file.Close()
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		fields := strings.Fields(scanner.Text())
		if len(fields) >= 2 && fields[0] == "Uid:" {
			uid, err := strconv.Atoi(fields[1])
			return uid, err == nil
		}
	}
	return 0, false
}

func clean(value string) string {
	return strings.NewReplacer("\t", " ", "\n", " ").Replace(value)
}
