package main

import (
	"reflect"
	"testing"
)

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
