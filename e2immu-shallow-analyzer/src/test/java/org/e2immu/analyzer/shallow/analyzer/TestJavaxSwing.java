package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.analysis.Value;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.COMMUTABLE_METHODS;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJavaxSwing extends CommonTest {

    @Test
    public void testJLabelSetText() {
        TypeInfo typeInfo = compiledTypesManager.getOrLoad(JLabel.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("setText", 1);
        assertTrue(methodInfo.isModifying());
    }

    @Test
    public void testJComboBoxAddActionListener() {
        TypeInfo typeInfo = compiledTypesManager.getOrLoad(JComboBox.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("addActionListener", 1);
        assertTrue(methodInfo.isModifying());
    }

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
        testCommutable(methodInfo);
    }

    @Test
    public void testJTableSetFillsViewportHeight() {
        TypeInfo typeInfo = compiledTypesManager.getOrLoad(JTable.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("setFillsViewportHeight", 1);
        assertTrue(methodInfo.isModifying());
        testCommutable(methodInfo);
    }

    @Test
    public void setJTableRowSelectionAllowed() {
        TypeInfo typeInfo = compiledTypesManager.getOrLoad(JTable.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("setRowSelectionAllowed", 1);
        assertTrue(methodInfo.isModifying());
        testCommutable(methodInfo);
    }

    private void testCommutable(MethodInfo methodInfo) {
        Value.CommutableData cd = methodInfo.analysis().getOrNull(COMMUTABLE_METHODS, ValueImpl.CommutableData.class);
        assertTrue(cd.isDefault());
    }

}
