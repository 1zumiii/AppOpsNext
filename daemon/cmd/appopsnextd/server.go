package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"os/exec"
	"regexp"
	"strconv"
	"strings"
	"time"
)

const (
	commandTimeout       = 10 * time.Second
	timeoutExitCode      = -1
	startFailureExitCode = -2
	maxRequestLength     = 1024
	maxResultBytes       = 16 * 1024 * 1024
)

var (
	packageNamePattern   = regexp.MustCompile(`^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$`)
	operationNamePattern = regexp.MustCompile(`^[A-Za-z0-9_.:-]+$`)
	allowedModes         = map[string]struct{}{
		"allow":      {},
		"ignore":     {},
		"deny":       {},
		"default":    {},
		"foreground": {},
	}
)

type appOpsCommand struct {
	arguments []string
}

type commandResult struct {
	exitCode int
	stdout   []byte
	stderr   []byte
	timedOut bool
}

func serve(reader *bufio.Reader, writer *bufio.Writer) error {
	for {
		request, err := reader.ReadString('\n')
		if err != nil {
			if errors.Is(err, io.EOF) {
				return nil
			}
			return fmt.Errorf("read request: %w", err)
		}
		if len(request) > maxRequestLength {
			return errors.New("request exceeds protocol limit")
		}
		request = strings.TrimSuffix(request, "\n")
		if request == "PING" {
			if err := writeLine(writer, "PONG"); err != nil {
				return err
			}
			continue
		}
		if request == "EXIT" {
			return writeLine(writer, "BYE")
		}

		command, err := parseCommand(request)
		if err != nil {
			return err
		}
		if err := writeResult(writer, execute(command)); err != nil {
			return err
		}
	}
}

func parseCommand(request string) (appOpsCommand, error) {
	fields := strings.Fields(request)
	if len(fields) == 0 {
		return appOpsCommand{}, errors.New("empty request")
	}

	switch fields[0] {
	case "GET_PACKAGE_OPS":
		if len(fields) != 2 || !validPackageName(fields[1]) {
			return appOpsCommand{}, errors.New("invalid package query")
		}
		return command("/system/bin/cmd", "appops", "get", fields[1]), nil

	case "GET_PACKAGE_OP":
		if len(fields) != 3 ||
			!validPackageName(fields[1]) ||
			!validOperationName(fields[2]) {
			return appOpsCommand{}, errors.New("invalid operation query")
		}
		return command(
			"/system/bin/cmd",
			"appops",
			"get",
			fields[1],
			fields[2],
		), nil

	case "GET_UID_OPS":
		if len(fields) != 2 {
			return appOpsCommand{}, errors.New("invalid UID query")
		}
		uid, err := strconv.Atoi(fields[1])
		if err != nil || uid < 0 {
			return appOpsCommand{}, errors.New("invalid Android UID")
		}
		return command(
			"/system/bin/cmd",
			"appops",
			"get",
			fields[1],
		), nil

	case "GET_HISTORY":
		if len(fields) != 2 || !validOperationName(fields[1]) {
			return appOpsCommand{}, errors.New("invalid history query")
		}
		return command(
			"/system/bin/dumpsys",
			"appops",
			"--history",
			"--include-discrete",
			"0",
			"--op",
			fields[1],
		), nil

	case "SET_PACKAGE":
		if !validModeCommand(fields) {
			return appOpsCommand{}, errors.New("invalid package mode request")
		}
		return command(
			"/system/bin/cmd",
			"appops",
			"set",
			fields[1],
			fields[2],
			fields[3],
		), nil

	case "SET_UID":
		if !validModeCommand(fields) {
			return appOpsCommand{}, errors.New("invalid UID mode request")
		}
		return command(
			"/system/bin/cmd",
			"appops",
			"set",
			"--uid",
			fields[1],
			fields[2],
			fields[3],
		), nil

	default:
		return appOpsCommand{}, errors.New("unsupported daemon command")
	}
}

func validModeCommand(fields []string) bool {
	if len(fields) != 4 ||
		!validPackageName(fields[1]) ||
		!validOperationName(fields[2]) {
		return false
	}
	_, validMode := allowedModes[fields[3]]
	return validMode
}

func validPackageName(value string) bool {
	return len(value) >= 1 &&
		len(value) <= 255 &&
		packageNamePattern.MatchString(value)
}

func validOperationName(value string) bool {
	return len(value) >= 1 &&
		len(value) <= 128 &&
		operationNamePattern.MatchString(value)
}

func command(arguments ...string) appOpsCommand {
	return appOpsCommand{arguments: arguments}
}

func execute(command appOpsCommand) commandResult {
	contextWithTimeout, cancel := context.WithTimeout(
		context.Background(),
		commandTimeout,
	)
	defer cancel()

	process := exec.CommandContext(
		contextWithTimeout,
		command.arguments[0],
		command.arguments[1:]...,
	)
	var stdout bytes.Buffer
	var stderr bytes.Buffer
	process.Stdout = &stdout
	process.Stderr = &stderr
	err := process.Run()

	result := commandResult{
		exitCode: 0,
		stdout:   bounded(stdout.Bytes()),
		stderr:   bounded(stderr.Bytes()),
		timedOut: errors.Is(contextWithTimeout.Err(), context.DeadlineExceeded),
	}
	if result.timedOut {
		result.exitCode = timeoutExitCode
		return result
	}
	if err == nil {
		return result
	}

	var exitError *exec.ExitError
	if errors.As(err, &exitError) {
		result.exitCode = exitError.ExitCode()
		return result
	}
	result.exitCode = startFailureExitCode
	result.stderr = bounded(
		append(
			append([]byte{}, result.stderr...),
			[]byte(err.Error())...,
		),
	)
	return result
}

func bounded(value []byte) []byte {
	if len(value) <= maxResultBytes {
		return value
	}
	return value[:maxResultBytes]
}

func writeResult(
	writer *bufio.Writer,
	result commandResult,
) error {
	line := strings.Join(
		[]string{
			"RESULT",
			strconv.Itoa(result.exitCode),
			strconv.FormatBool(result.timedOut),
			encodeField(result.stdout),
			encodeField(result.stderr),
		},
		"|",
	)
	return writeLine(writer, line)
}

func encodeField(value []byte) string {
	if len(value) == 0 {
		return "-"
	}
	return base64.StdEncoding.EncodeToString(value)
}

func writeLine(writer *bufio.Writer, value string) error {
	if _, err := writer.WriteString(value + "\n"); err != nil {
		return fmt.Errorf("write response: %w", err)
	}
	if err := writer.Flush(); err != nil {
		return fmt.Errorf("flush response: %w", err)
	}
	return nil
}
