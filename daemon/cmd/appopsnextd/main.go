package main

import (
	"bufio"
	"errors"
	"flag"
	"fmt"
	"os"
	"regexp"
)

const protocolVersion = 1

var tokenPattern = regexp.MustCompile(`^[a-f0-9]{64}$`)

type configuration struct {
	token string
}

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run() error {
	config, err := readConfiguration()
	if err != nil {
		return err
	}

	reader := bufio.NewReader(os.Stdin)
	writer := bufio.NewWriter(os.Stdout)
	if err := authenticate(reader, writer, config.token); err != nil {
		return err
	}
	if err := probe(reader, writer); err != nil {
		return err
	}
	return nil
}

func readConfiguration() (configuration, error) {
	var config configuration
	flag.StringVar(&config.token, "token", "", "authentication token")
	flag.Parse()

	switch {
	case !tokenPattern.MatchString(config.token):
		return configuration{}, errors.New("invalid authentication token")
	default:
		return config, nil
	}
}

func authenticate(
	reader *bufio.Reader,
	writer *bufio.Writer,
	token string,
) error {
	request, err := reader.ReadString('\n')
	if err != nil {
		return fmt.Errorf("read handshake: %w", err)
	}
	expected := fmt.Sprintf("HELLO %d %s\n", protocolVersion, token)
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

func probe(reader *bufio.Reader, writer *bufio.Writer) error {
	request, err := reader.ReadString('\n')
	if err != nil {
		return fmt.Errorf("read probe: %w", err)
	}
	if request != "PING\n" {
		return errors.New("invalid probe")
	}
	if _, err := writer.WriteString("PONG\n"); err != nil {
		return fmt.Errorf("write probe: %w", err)
	}
	if err := writer.Flush(); err != nil {
		return fmt.Errorf("flush probe: %w", err)
	}
	return nil
}
