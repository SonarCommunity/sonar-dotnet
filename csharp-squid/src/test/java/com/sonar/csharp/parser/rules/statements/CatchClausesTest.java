/*
 * Copyright (C) 2010 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.csharp.parser.rules.statements;

import static com.sonar.sslr.test.parser.ParserMatchers.parse;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.sonar.csharp.api.CSharpGrammar;
import com.sonar.csharp.parser.CSharpParser;

public class CatchClausesTest {

  CSharpParser p = new CSharpParser();
  CSharpGrammar g = p.getGrammar();

  @Before
  public void init() {
    p.setRootRule(g.catchClauses);
    g.specificCatchClause.mock();
    g.generalCatchClause.mock();
  }

  @Test
  public void testOk() {
    assertThat(p, parse("specificCatchClause"));
    assertThat(p, parse("specificCatchClause specificCatchClause specificCatchClause"));
    assertThat(p, parse("specificCatchClause generalCatchClause"));
    assertThat(p, parse("specificCatchClause specificCatchClause specificCatchClause generalCatchClause"));
    assertThat(p, parse("generalCatchClause"));
  }

}
