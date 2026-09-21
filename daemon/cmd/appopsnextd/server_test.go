package main

import (
	"bufio"
	"errors"
	"io"
	"reflect"
	"strings"
	"testing"
)

var errReadFailure = errors.New("read failure")

type failingReader struct{}

func (failingReader) Read([]byte) (int, error) {
	return 0, errReadFailure
}

func TestParsePackageOperationQuery(t *testing.T) {
	actual, err := parseCommand(
		"GET_PACKAGE_OP dev.izumi.appopsnext android:camera",
	)
	if err != nil {
		t.Fatal(err)
	}
	expected := []string{
		"/system/bin/cmd",
		"appops",
		"get",
		"dev.izumi.appopsnext",
		"android:camera",
	}
	if !reflect.DeepEqual(actual.arguments, expected) {
		t.Fatalf("arguments = %#v, want %#v", actual.arguments, expected)
	}
}

func TestParseRejectsShellInjection(t *testing.T) {
	_, err := parseCommand(
		"GET_PACKAGE_OP dev.izumi.appopsnext android:camera;id",
	)
	if err == nil {
		t.Fatal("expected operation injection to be rejected")
	}
}

func TestParseRejectsUnsupportedMode(t *testing.T) {
	_, err := parseCommand(
		"SET_PACKAGE dev.izumi.appopsnext android:camera surprise",
	)
	if err == nil {
		t.Fatal("expected unsupported mode to be rejected")
	}
}

func TestReadRequestStripsTheDelimiter(t *testing.T) {
	reader := bufio.NewReader(strings.NewReader("PING\n"))
	request, err := readRequest(reader)
	if err != nil {
		t.Fatal(err)
	}
	if request != "PING" {
		t.Fatalf("request = %q, want %q", request, "PING")
	}
}

func TestReadRequestAcceptsTheLongestAllowedLine(t *testing.T) {
	line := strings.Repeat("A", maxRequestLength-1) + "\n"
	reader := bufio.NewReader(strings.NewReader(line))
	request, err := readRequest(reader)
	if err != nil {
		t.Fatal(err)
	}
	if len(request) != maxRequestLength-1 {
		t.Fatalf("length = %d, want %d", len(request), maxRequestLength-1)
	}
}

func TestReadRequestContinuesAfterBufferFull(t *testing.T) {
	line := strings.Repeat("A", 64) + "\n"
	reader := bufio.NewReaderSize(strings.NewReader(line), 16)
	request, err := readRequest(reader)
	if err != nil {
		t.Fatal(err)
	}
	if request != strings.TrimSuffix(line, "\n") {
		t.Fatalf("request = %q, want %q", request, strings.TrimSuffix(line, "\n"))
	}
}

func TestReadRequestRejectsAnOversizedLine(t *testing.T) {
	line := strings.Repeat("A", maxRequestLength) + "\n"
	reader := bufio.NewReader(strings.NewReader(line))
	if _, err := readRequest(reader); err == nil {
		t.Fatal("expected an oversized request to be rejected")
	}
}

func TestReadRequestRejectsAnOversizedUnterminatedLine(t *testing.T) {
	line := strings.Repeat("A", maxRequestLength+16)
	reader := bufio.NewReaderSize(strings.NewReader(line), 16)
	if _, err := readRequest(reader); err == nil {
		t.Fatal("expected an oversized unterminated request to be rejected")
	}
}

func TestReadRequestReportsEndOfStream(t *testing.T) {
	reader := bufio.NewReader(strings.NewReader(""))
	if _, err := readRequest(reader); !errors.Is(err, io.EOF) {
		t.Fatalf("err = %v, want %v", err, io.EOF)
	}
}

func TestReadRequestDiscardsPartialLineAtEndOfStream(t *testing.T) {
	reader := bufio.NewReader(strings.NewReader("PING"))
	if _, err := readRequest(reader); !errors.Is(err, io.EOF) {
		t.Fatalf("err = %v, want %v", err, io.EOF)
	}
}

func TestReadRequestLeavesFollowingLinesForTheNextCall(t *testing.T) {
	reader := bufio.NewReaderSize(strings.NewReader("PING\nEXIT\n"), 16)
	for _, want := range []string{"PING", "EXIT"} {
		request, err := readRequest(reader)
		if err != nil {
			t.Fatal(err)
		}
		if request != want {
			t.Fatalf("request = %q, want %q", request, want)
		}
	}
}

func TestReadRequestWrapsReaderFailures(t *testing.T) {
	reader := bufio.NewReader(failingReader{})
	if _, err := readRequest(reader); !errors.Is(err, errReadFailure) {
		t.Fatalf("err = %v, want wrapped %v", err, errReadFailure)
	}
}

// The unfiltered dump runs to tens of thousands of lines, so the query the
// monitor uses to read process state has to stay scoped to one package.
func TestParseUidStateQueryIsScopedToOnePackage(t *testing.T) {
	actual, err := parseCommand("GET_UID_STATES dev.izumi.appopsnext")
	if err != nil {
		t.Fatal(err)
	}
	expected := []string{
		"/system/bin/dumpsys",
		"appops",
		"--package",
		"dev.izumi.appopsnext",
	}
	if !reflect.DeepEqual(actual.arguments, expected) {
		t.Fatalf("arguments = %#v, want %#v", actual.arguments, expected)
	}
}

func TestParseRejectsMalformedUidStateQuery(t *testing.T) {
	for _, request := range []string{
		"GET_UID_STATES",
		"GET_UID_STATES dev.izumi.appopsnext extra",
		"GET_UID_STATES dev.izumi.appopsnext;id",
		"GET_UID_STATES ../../etc/passwd",
	} {
		if _, err := parseCommand(request); err == nil {
			t.Fatalf("expected %q to be rejected", request)
		}
	}
}
