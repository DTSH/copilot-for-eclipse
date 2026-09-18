// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CustomAgentToolFileUpdaterTest {

  @Test
  void testUpdateToolsInContent_missingTools_insertsToolsBeforeFrontmatterEnd() {
    String content = "---\n"
        + "description: Test agent\n"
        + "---\n"
        + "Instructions\n";

    String updatedContent = CustomAgentToolFileUpdater.updateToolsInContent(content,
        List.of("read_file", "custom-server/search"));

    assertEquals("---\n"
        + "description: Test agent\n"
        + "tools:\n"
        + "  - \"custom-server/search\"\n"
        + "  - \"read_file\"\n"
        + "---\n"
        + "Instructions\n", updatedContent);
  }

  @Test
  void testUpdateToolsInContent_existingInlineTools_replacesWithEmptyList() {
    String content = "---\n"
        + "description: Test agent\n"
        + "tools: [old_tool, old-server/old_tool]\n"
        + "model: gpt-4.1\n"
        + "---\n"
        + "Instructions\n";

    String updatedContent = CustomAgentToolFileUpdater.updateToolsInContent(content, List.of());

    assertEquals("---\n"
        + "description: Test agent\n"
        + "tools: []\n"
        + "model: gpt-4.1\n"
        + "---\n"
        + "Instructions\n", updatedContent);
  }

  @Test
  void testUpdateToolsInContent_existingBlockTools_keepsFollowingFrontmatterKeys() {
    String content = "---\n"
        + "description: Test agent\n"
        + "tools:\n"
        + "  - old_tool\n"
        + "  - old-server/old_tool\n"
        + "model: gpt-4.1\n"
        + "---\n"
        + "Instructions\n";

    String updatedContent = CustomAgentToolFileUpdater.updateToolsInContent(content, List.of("new_tool"));

    assertEquals("---\n"
        + "description: Test agent\n"
        + "tools:\n"
        + "  - \"new_tool\"\n"
        + "model: gpt-4.1\n"
        + "---\n"
        + "Instructions\n", updatedContent);
  }

  @Test
  void testToToolSpecifications_enabledTools_includeBuiltInAndMcpTools() {
    Map<String, Map<String, Boolean>> serverToolStatus = new LinkedHashMap<>();
    Map<String, Boolean> builtInTools = new LinkedHashMap<>();
    builtInTools.put("read_file", true);
    builtInTools.put("run_in_terminal", false);
    serverToolStatus.put("Built-in Tools", builtInTools);

    Map<String, Boolean> customServerTools = new LinkedHashMap<>();
    customServerTools.put("search", true);
    customServerTools.put("disabled", false);
    serverToolStatus.put("custom-server", customServerTools);

    List<String> toolSpecifications = CustomAgentToolFileUpdater.toToolSpecifications(serverToolStatus,
        "Built-in Tools");

    assertEquals(List.of("custom-server/search", "read_file"), toolSpecifications);
  }
}
