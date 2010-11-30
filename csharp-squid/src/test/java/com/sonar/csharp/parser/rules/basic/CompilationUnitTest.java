/*
 * Copyright (C) 2010 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.csharp.parser.rules.basic;

import static com.sonar.sslr.test.parser.ParserMatchers.notParse;
import static com.sonar.sslr.test.parser.ParserMatchers.parse;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.sonar.csharp.api.CSharpGrammar;
import com.sonar.csharp.parser.CSharpParser;

public class CompilationUnitTest {

  CSharpParser p = new CSharpParser();
  CSharpGrammar g = p.getGrammar();

  @Before
  public void init() {
    g.externAliasDirective.mock();
    g.usingDirective.mock();
    g.globalAttributes.mock();
    g.namespaceMemberDeclaration.mock();
  }

  @Test
  public void testOk() {
    assertThat(p, parse("externAliasDirective"));
    assertThat(p, parse("externAliasDirective externAliasDirective"));
    assertThat(p, parse("usingDirective"));
    assertThat(p, parse("usingDirective usingDirective"));
    assertThat(p, parse("globalAttributes"));
    assertThat(p, parse("namespaceMemberDeclaration"));
    assertThat(p, parse("namespaceMemberDeclaration namespaceMemberDeclaration"));
    assertThat(p, parse("externAliasDirective usingDirective globalAttributes namespaceMemberDeclaration"));
    assertThat(p, parse("externAliasDirective externAliasDirective usingDirective globalAttributes namespaceMemberDeclaration"));
  }

  @Test
  public void testKo() {
    assertThat(p, notParse("namespaceMemberDeclaration externAliasDirective"));
  }

}
