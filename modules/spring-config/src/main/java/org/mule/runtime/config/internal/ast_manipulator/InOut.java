/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.ast_manipulator;

import java.util.ArrayList;
import java.util.List;

public class InOut {

  private List<String> in;
  private String out;

  public InOut(List<String> in, String out) {
    this.in = in;
    this.out = out;
  }

  public List<String> getIn() {
    return this.in;
  }

  public String getOut() {
    return out;
  }

  public static class Builder {

    private List<String> ins = new ArrayList<>();
    private String out;

    public Builder withIn(String in) {
      ins.add(in);
      return this;
    }

    public Builder withOut(String out) {
      this.out = out;
      return this;
    }

    public InOut build() {
      return new InOut(ins, out);
    }
  }
}
