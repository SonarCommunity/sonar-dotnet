/*
 * Copyright (C) 2010 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.csharp.parser.rules.classes;

import static com.sonar.sslr.test.parser.ParserMatchers.parse;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.sonar.csharp.api.CSharpGrammar;
import com.sonar.csharp.parser.CSharpParser;

public class EventAccessorDeclarationTest {

  CSharpParser p = new CSharpParser();
  CSharpGrammar g = p.getGrammar();

  @Before
  public void init() {
    p.setRootRule(g.eventAccessorDeclarations);
    g.attributes.mock();
    g.block.mock();
  }

  @Test
  public void testOk() {
    assertThat(p, parse("add block remove block"));
    assertThat(p, parse("remove block add block"));
    assertThat(p, parse("attributes add block attributes remove block"));
    assertThat(p, parse("attributes remove block attributes add block"));
  }

}
