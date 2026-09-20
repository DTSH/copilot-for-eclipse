// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.core.chat;

import java.util.List;

import com.microsoft.copilot.eclipse.core.lsp.protocol.ConversationMode;

/**
 * Represents a custom chat mode created by the user.
 */
public class CustomChatMode extends BaseChatMode {
  private boolean hasExplicitToolList;

  /**
   * Constructor for CustomChatMode.
   *
   * @param id the unique identifier
   * @param displayName the display name
   * @param description the description
   */
  public CustomChatMode(String id, String displayName, String description) {
    super(id, displayName, description);
    this.hasExplicitToolList = true;
  }

  /**
   * Constructor for CustomChatMode from ConversationMode API response.
   * This is the primary constructor used when loading modes from the LSP API.
   *
   * @param mode the ConversationMode from API
   */
  public CustomChatMode(ConversationMode mode) {
    super(mode.getId(), mode.getName(), mode.getDescription(),
        mode.getCustomTools(), mode.getModel(), mode.getHandOffs());
    this.hasExplicitToolList = mode.getCustomTools() != null;
  }

  /**
   * Gibt an, ob die Agent-Datei eine explizite {@code tools}-Liste enthält.
   *
   * @return {@code true}, wenn {@code tools} in der Agent-Definition vorhanden ist
   */
  public boolean hasExplicitToolList() {
    return hasExplicitToolList;
  }

  @Override
  public void setTools(List<String> tools) {
    super.setTools(tools);
    this.hasExplicitToolList = tools != null;
  }

  @Override
  public boolean allowsToolConfiguration() {
    return true; // Custom modes allow tool configuration
  }

  @Override
  public boolean isBuiltIn() {
    return false;
  }
}
