// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.utils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.copilot.eclipse.core.lsp.CopilotLanguageServerConnection;
import com.microsoft.copilot.eclipse.core.lsp.mcp.McpRegistryAllowList;

@ExtendWith(MockitoExtension.class)
class McpUtilsTests {
  @Mock
  private CopilotLanguageServerConnection mockConnection;

  @Test
  void testGetMcpAllowListInvalidFormatDirect_returnsNull() {
    when(mockConnection.getMcpAllowlist(any())).thenReturn(CompletableFuture.failedFuture(
        new IllegalArgumentException("Invalid allowlist format: expected object")));

    assertNull(McpUtils.getMcpAllowList(mockConnection).join());
  }

  @Test
  void testGetMcpAllowListInvalidFormatWrapped_returnsNull() {
    RuntimeException cause = new RuntimeException("Invalid allowlist format: expected object");
    when(mockConnection.getMcpAllowlist(any())).thenReturn(CompletableFuture.failedFuture(
        new CompletionException(cause)));

    assertNull(McpUtils.getMcpAllowList(mockConnection).join());
  }

  @Test
  void testGetMcpAllowListSuccess_returnsResponse() {
    McpRegistryAllowList allowList = new McpRegistryAllowList();
    when(mockConnection.getMcpAllowlist(any())).thenReturn(CompletableFuture.completedFuture(allowList));

    assertSame(allowList, McpUtils.getMcpAllowList(mockConnection).join());
  }

  @Test
  void testGetMcpAllowListUnexpectedError_remainsExceptional() {
    when(mockConnection.getMcpAllowlist(any())).thenReturn(CompletableFuture.failedFuture(
        new IllegalStateException("Server unavailable")));

    assertThrows(CompletionException.class, () -> McpUtils.getMcpAllowList(mockConnection).join());
  }
}
