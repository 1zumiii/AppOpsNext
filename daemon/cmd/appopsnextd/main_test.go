package main

import (
	"bufio"
	"bytes"
	"errors"
	"io"
	"strings"
	"testing"
)

func TestAuthenticateAcceptsHandshakeAndPreservesFollowingRequest(t *testing.T) {
	reader := bufio.NewReaderSize(
		strings.NewReader("HELLO 1\nPING\n"),
		16,
	)
	var output bytes.Buffer
	writer := bufio.NewWriter(&output)

	if err := authenticate(reader, writer); err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(output.String(), "READY 1 ") {
		t.Fatalf("response = %q, want READY handshake", output.String())
	}
	request, err := readRequest(reader)
	if err != nil {
		t.Fatal(err)
	}
	if request != "PING" {
		t.Fatalf("request = %q, want %q", request, "PING")
	}
}

func TestAuthenticateRejectsOversizedHandshake(t *testing.T) {
	reader := bufio.NewReaderSize(
		strings.NewReader(strings.Repeat("A", maxRequestLength+16)),
		16,
	)
	writer := bufio.NewWriter(io.Discard)

	err := authenticate(reader, writer)
	if err == nil || !strings.Contains(err.Error(), "request exceeds protocol limit") {
		t.Fatalf("err = %v, want protocol-limit error", err)
	}
}

func TestAuthenticateWrapsPartialHandshakeEndOfStream(t *testing.T) {
	reader := bufio.NewReader(strings.NewReader("HELLO 1"))
	writer := bufio.NewWriter(io.Discard)

	err := authenticate(reader, writer)
	if !errors.Is(err, io.EOF) {
		t.Fatalf("err = %v, want %v", err, io.EOF)
	}
}
