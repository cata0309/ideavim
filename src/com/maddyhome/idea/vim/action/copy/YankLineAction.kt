/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2019 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.action.copy;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.action.VimCommandAction;
import com.maddyhome.idea.vim.command.Command;
import com.maddyhome.idea.vim.command.MappingMode;
import com.maddyhome.idea.vim.handler.VimActionHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Set;


public class YankLineAction extends VimCommandAction {
  public YankLineAction() {
    super(new Handler());
  }

  @NotNull
  @Override
  public Set<MappingMode> getMappingModes() {
    return null;
  }

  @NotNull
  @Override
  public Set<List<KeyStroke>> getKeyStrokesSet() {
    return null;
  }

  @NotNull
  @Override
  public Command.Type getType() {
    return null;
  }

  @NotNull
  @Override
  protected VimActionHandler makeActionHandler() {
    return null;
  }

  private static class Handler extends VimActionHandler.SingleExecution {
    @Override
    public boolean execute(@NotNull Editor editor, @NotNull DataContext context, @NotNull Command cmd) {
      return VimPlugin.getYank().yankLine(editor, cmd.getCount());
    }
  }
}