/***
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000,2002,2003 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.objectweb.asm.util;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.attrs.ASMifiable;

/**
 * An abstract ASMifier visitor.
 *
 * @author Eric Bruneton
 */

public class ASMifierAbstractVisitor extends AbstractVisitor {

  /**
   * The name of the variable for this visitor in the produced code.
   */

  protected String name;

  /**
   * Constructs a new {@link ASMifierAbstractVisitor}.
   *
   * @param name the name of the variable for this visitor in the produced code.
   */

  protected ASMifierAbstractVisitor (final String name) {
    this.name = name;
  }

  /**
   * Prints the ASM code that generates the given annotation.
   *
   * @param desc the class descriptor of the annotation class.
   * @param visible <tt>true</tt> if the annotation is visible at runtime.
   * @return a visitor to visit the annotation values.
   */

  public AnnotationVisitor visitAnnotation (
    final String desc,
    final boolean visible)
  {
    buf.setLength(0);
    buf.append("{\n").append("av0 = ").append(name).append(".visitAnnotation(");
    appendConstant(buf, desc);
    buf.append(", ").append(visible).append(");\n");
    text.add(buf.toString());
    ASMifierAnnotationVisitor av = new ASMifierAnnotationVisitor(0);
    text.add(av.getText());
    text.add("}\n");
    return av;
  }

  /**
   * Prints the ASM code that generates the given attribute.
   *
   * @param attr an attribute.
   */

  public void visitAttribute (final Attribute attr) {
    buf.setLength(0);
    if (attr instanceof ASMifiable) {
      buf.append("{\n");
      buf.append("// ATTRIBUTE\n");
      ((ASMifiable)attr).asmify(buf, "attr", null);
      buf.append(name).append(".visitAttribute(attr);\n");
      buf.append("}\n");
    } else {
      buf.append("// WARNING! skipped a non standard attribute of type \"");
      buf.append(attr.type).append("\"\n");
    }
    text.add(buf.toString());
  }

  /**
   * Prints the ASM code to end the visit.
   */
  
  public void visitEnd () {
    buf.setLength(0);
    buf.append(name).append(".visitEnd();\n");
    text.add(buf.toString());
  }
  
  /**
   * Appends a string representation of the given constant to the given buffer.
   *
   * @param buf a string buffer.
   * @param cst an {@link Integer}, {@link Float}, {@link Long}, {@link Double}
   *      or {@link String} object. May be <tt>null</tt>.
   */

  static void appendConstant (final StringBuffer buf, final Object cst) {
    if (cst == null) {
      buf.append("null");
    } else if (cst instanceof String) {
      String s = (String)cst;
      buf.append("\"");
      for (int i = 0; i < s.length(); ++i) {
        char c = s.charAt(i);
        if (c == '\n') {
          buf.append("\\n");
        } else if (c == '\r') {
           buf.append("\\r");
        } else if (c == '\\') {
          buf.append("\\\\");
        } else if (c == '"') {
          buf.append("\\\"");
        } else if (c < 0x20 || c > 0x7f) {
          buf.append("\\u");
          if (c < 0x10) {
            buf.append("000");
          } else if (c < 0x100) {
            buf.append("00");
          } else if (c < 0x1000) {
            buf.append("0");
          }
          buf.append(Integer.toString( c, 16));
        } else {
          buf.append(c);
        }
      }
      buf.append("\"");
    } else if (cst instanceof Type) {
      buf.append("Type.getType(\"");
      buf.append(((Type)cst).getDescriptor());
      buf.append("\")");
    } else if (cst instanceof Integer) {
      buf.append("new Integer(").append(cst).append(")");
    } else if (cst instanceof Float) {
      buf.append("new Float(").append(cst).append("F)");
    } else if (cst instanceof Long) {
      buf.append("new Long(").append(cst).append("L)");
    } else if (cst instanceof Double) {
      buf.append("new Double(").append(cst).append(")");
    }
  }
}
