// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.utils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

import com.microsoft.copilot.eclipse.core.lsp.CopilotLanguageServerConnection;
import com.microsoft.copilot.eclipse.core.lsp.mcp.McpRegistryAllowList;

class McpUtilsTests {

  private static final String INVALID_ALLOWLIST_MESSAGE =
      "Invalid allowlist format: registry entries missing required fields";

  @Test
  void testGetMcpAllowList_invalidAllowlistFormat_returnsNoAllowlist() {
    CopilotLanguageServerConnection connection = mock(CopilotLanguageServerConnection.class);
    when(connection.getMcpAllowlist(any())).thenReturn(CompletableFuture.failedFuture(
        new IllegalStateException(INVALID_ALLOWLIST_MESSAGE)));

    McpRegistryAllowList allowList = McpUtils.getMcpAllowList(connection).join();

    assertNull(allowList);
  }

  @Test
  void testGetMcpAllowList_wrappedInvalidAllowlistFormat_returnsNoAllowlist() {
    CopilotLanguageServerConnection connection = mock(CopilotLanguageServerConnection.class);
    when(connection.getMcpAllowlist(any())).thenReturn(CompletableFuture.failedFuture(
        new CompletionException(new IllegalStateException(INVALID_ALLOWLIST_MESSAGE))));

    McpRegistryAllowList allowList = McpUtils.getMcpAllowList(connection).join();

    assertNull(allowList);
  }

  @Test
  void testGetMcpAllowList_successfulResponse_returnsAllowlist() {
    CopilotLanguageServerConnection connection = mock(CopilotLanguageServerConnection.class);
    McpRegistryAllowList expectedAllowList = new McpRegistryAllowList();
    expectedAllowList.setMcpRegistries(List.of());
    when(connection.getMcpAllowlist(any())).thenReturn(CompletableFuture.completedFuture(expectedAllowList));

    McpRegistryAllowList allowList = McpUtils.getMcpAllowList(connection).join();

    assertSame(expectedAllowList, allowList);
  }

  @Test
  void testGetMcpAllowList_unexpectedFailure_remainsExceptional() {
    CopilotLanguageServerConnection connection = mock(CopilotLanguageServerConnection.class);
    IllegalStateException expectedFailure = new IllegalStateException("Unexpected failure");
    when(connection.getMcpAllowlist(any())).thenReturn(CompletableFuture.failedFuture(expectedFailure));

    CompletionException actualFailure = assertThrows(CompletionException.class,
        () -> McpUtils.getMcpAllowList(connection).join());

    assertSame(expectedFailure, actualFailure.getCause());
  }
}
