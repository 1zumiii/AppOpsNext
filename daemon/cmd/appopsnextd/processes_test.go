package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func writeProcess(t *testing.T, root string, pid string, cmdline string, uid string) {
	t.Helper()
	directory := filepath.Join(root, pid)
	if err := os.MkdirAll(directory, 0o755); err != nil {
		t.Fatal(err)
	}
	files := map[string]string{
		"cmdline": cmdline,
		"stat":    pid + " (short name) S 1 0 0 0 0 0 0 0 0 0 0 0 0 0 20 0 1 0 4200 0 0\n",
		"status":  "Name:\tshort\nUid:\t" + uid + "\t" + uid + "\t" + uid + "\t" + uid + "\n",
	}
	for name, content := range files {
		if err := os.WriteFile(filepath.Join(directory, name), []byte(content), 0o644); err != nil {
			t.Fatal(err)
		}
	}
}

func TestCollectProcessesListsOnlyRelevantOnes(t *testing.T) {
	root := t.TempDir()
	writeProcess(t, root, "100", "dev.izumi.appopsnext\x00", "10234")
	writeProcess(t, root, "101", "dev.izumi.appopsnext:watcher\x00", "2000")
	writeProcess(t, root, "102", "/data/local/tmp/appopsnext/42/appopsnextd\x00", "2000")
	writeProcess(t, root, "103", "shizuku_server\x00", "2000")
	writeProcess(t, root, "104", "com.example.other\x00", "10500")
	writeProcess(t, root, "105", "dev.izumi.appopsnext.other\x00", "10501")
	writeProcess(t, root, "106", "", "0")
	if err := os.MkdirAll(filepath.Join(root, "self"), 0o755); err != nil {
		t.Fatal(err)
	}

	result := collectProcesses(root, "dev.izumi.appopsnext")

	if result.exitCode != 0 {
		t.Fatalf("exit code = %d, stderr = %s", result.exitCode, result.stderr)
	}
	lines := strings.Split(string(result.stdout), "\n")
	if len(lines) != 4 {
		t.Fatalf("lines = %q", lines)
	}
	for _, unwanted := range []string{"com.example.other", "dev.izumi.appopsnext.other"} {
		if strings.Contains(string(result.stdout), unwanted) {
			t.Fatalf("listed %s: %q", unwanted, lines)
		}
	}
	if !strings.Contains(string(result.stdout), "2000\tdev.izumi.appopsnext:watcher\t101 (short name) S 1") {
		t.Fatalf("watcher line missing: %q", lines)
	}
}

func TestParseProcessQuery(t *testing.T) {
	command, err := parseCommand("GET_PROCESSES dev.izumi.appopsnext")
	if err != nil || command.run == nil {
		t.Fatalf("command = %#v, error = %v", command, err)
	}
	for _, request := range []string{"GET_PROCESSES", "GET_PROCESSES a b", "GET_PROCESSES ../etc"} {
		if _, err := parseCommand(request); err == nil {
			t.Fatalf("accepted malformed request %q", request)
		}
	}
}
