package main

import (
	"bufio"
	"errors"
	"fmt"
	"os"
)

const protocolVersion = 1

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run() error {
	reader := bufio.NewReader(os.Stdin)
	writer := bufio.NewWriter(os.Stdout)
	if err := authenticate(reader, writer); err != nil {
		return err
	}
	if err := serve(reader, writer); err != nil {
		return err
	}
	return nil
}

func authenticate(
	reader *bufio.Reader,
	writer *bufio.Writer,
) error {
	request, err := reader.ReadString('\n')
	if err != nil {
		return fmt.Errorf("read handshake: %w", err)
	}
	expected := fmt.Sprintf("HELLO %d\n", protocolVersion)
	if request != expected {
		return errors.New("invalid handshake")
	}

	if _, err := fmt.Fprintf(
		writer,
		"READY %d %d %d\n",
		protocolVersion,
		os.Getuid(),
		os.Getpid(),
	); err != nil {
		return fmt.Errorf("write handshake: %w", err)
	}
	if err := writer.Flush(); err != nil {
		return fmt.Errorf("flush handshake: %w", err)
	}
	return nil
}
