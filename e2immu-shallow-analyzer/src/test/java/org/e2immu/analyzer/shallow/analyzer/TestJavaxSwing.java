package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.text.JTextComponent;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJavaxSwing extends CommonTest {

    @Test
    public void testAbstractButtonAddActionListener() {
        TypeInfo typeInfo = compiledTypesManager.getOrLoad(AbstractButton.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("addActionListener", 1);
        assertTrue(methodInfo.isModifying());
    }

    @Test
    public void testJTableSetDefaultRenderer() {
        TypeInfo typeInfo = compiledTypesManager.getOrLoad(JTable.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("setDefaultRenderer", 2);
        assertTrue(methodInfo.isModifying());
    }

}
