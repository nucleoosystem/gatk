/*
 * Copyright (c) 2010, The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.broadinstitute.sting.queue.extensions.gatk;

import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileWriter;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.broadinstitute.sting.gatk.filters.PlatformUnitFilterHelper;
import org.broadinstitute.sting.utils.genotype.GenotypeWriter;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.*;

public abstract class ArgumentField {

    public Collection<String> getImportStatements() {
        List<String> imports = new ArrayList<String>();
        for (Class<?> importClass: getImportClasses()) {
            if (!isBuiltIn(importClass))
                imports.add("import " + importClass.getName().replace("$", "."));
        }
        return imports;
    }

    /**
     * Returns true if a class is built in and doesn't need to be imported.
     * @param argType The class to check.
     * @return true if the class is built in and doesn't need to be imported
     */
    private static boolean isBuiltIn(Class<?> argType) {
        return argType.isPrimitive() || argType == String.class || Number.class.isAssignableFrom(argType);
    }

    /** @return Scala code defining the argument and it's annotation. */
    public final String getArgumentAddition() {
        return String.format("%n" +
                "/** %s */%n" +
                "@%s(fullName=\"%s\", shortName=\"%s\", doc=\"%s\", required=%s, exclusiveOf=\"%s\", validation=\"%s\")%n" +
                "%svar %s: %s = %s%n" +
                "%s",
                getDoc(),
                getAnnotationIOClass().getSimpleName(),
                getFullName(),
                getShortName(),
                getDoc(),
                isRequired(),
                getExclusiveOf(),
                getValidation(),
                getScatterGatherAnnotation(), getFieldName(), getFieldType(), getDefaultValue(),
                getDefineAddition());
    }

    /** @return Scala code with defines to append to the argument definition. */
    protected String getDefineAddition() { return ""; }

    /** @return Scala code to append to the command line. */
    public abstract String getCommandLineAddition();

    // Argument Annotation

    /** @return Documentation for the annotation. */
    protected abstract String getDoc();

    /** @return Annotation class of the annotation. */
    protected abstract Class<? extends Annotation> getAnnotationIOClass();

    /** @return Full name for the annotation. */
    protected abstract String getFullName();

    /** @return Short name for the annotation or "". */
    protected String getShortName() { return ""; }

    /** @return true if the argument is required. */
    protected abstract boolean isRequired();

    /** @return A comma separated list of arguments that may be substituted for this field. */
    protected String getExclusiveOf() { return ""; }

    /** @return A validation string for the argument. */
    protected String getValidation() { return ""; }

    /** @return A scatter or gather annotation with a line feed, or "". */
    protected String getScatterGatherAnnotation() { return ""; }

    // Scala

    /** @return The scala field type. */
    protected abstract String getFieldType();

    /** @return The scala default value. */
    protected abstract String getDefaultValue();

    /** @return The class of the field, or the component type if the scala field is a collection. */
    protected abstract Class<?> getInnerType();

    /** @return A custom command for overriding freeze. */
    protected String getFreezeFields() { return ""; }

    @SuppressWarnings("unchecked")
    protected Collection<Class<?>> getImportClasses() {
        return Arrays.asList(this.getInnerType(), getAnnotationIOClass());
    }

    /** @return True if this field uses @Scatter. */
    public boolean isScatter() { return false; }

    /** @return True if this field uses @Gather. */
    public boolean isGather() { return false; }

    /** @return The raw field name, which will be checked against scala build in types. */
    protected abstract String getRawFieldName();
    /** @return The field name checked against reserved words. */
    protected final String getFieldName() {
        return getFieldName(this.getRawFieldName());
    }

    /**
     * @param rawFieldName The raw field name
     * @return The field name checked against reserved words.
     */
    protected static String getFieldName(String rawFieldName) {
        String fieldName = rawFieldName;
        if (StringUtils.isNumeric(fieldName.substring(0,1)))
            fieldName = "_" + fieldName;
        if (isReserved(fieldName) || fieldName.contains("-"))
            fieldName = "`" + fieldName + "`";
        return fieldName;
    }

    /** via http://www.scala-lang.org/sites/default/files/linuxsoft_archives/docu/files/ScalaReference.pdf */
    private static final List<String> reservedWords = Arrays.asList(
            "abstract", "case", "catch", "class", "def",
            "do", "else", "extends", "false", "final",
            "finally", "for", "forSome", "if", "implicit",
            "import", "lazy", "match", "new", "null",
            "object", "override", "package", "private", "protected",
            "return", "sealed", "super", "this", "throw",
            "trait", "try", "true", "type", "val",
            "var", "while", "with", "yield");

    protected static boolean isReserved(String word) {
        return reservedWords.contains(word);
    }

    /**
     * On primitive types returns the capitalized scala type.
     * @param argType The class to check for options.
     * @return the simple name of the class.
     */
    protected static String getType(Class<?> argType) {
        String type = argType.getSimpleName();

        if (argType.isPrimitive())
            type = StringUtils.capitalize(type);

        if ("Integer".equals(type))
            type = "Int";

        return type;
    }

    protected static String escape(String string) {
        return (string == null) ? "" : StringEscapeUtils.escapeJava(string);
    }

    /**
     * @param argType The class to check for options.
     * @return true if option should be used.
     */
    protected static boolean useOption(Class<?> argType) {
        return (argType.isPrimitive()) || (Number.class.isAssignableFrom(argType)) || (argType.isEnum());
    }

    /**
     * @param argType The class to check for options.
     * @return true if option should be used.
     */
    protected static boolean useFormatter(Class<?> argType) {
        return (argType.equals(Double.class) || argType.equals(Double.TYPE) ||
                argType.equals(Float.class) || argType.equals(Float.TYPE));
    }

    // TODO: Use an annotation, type descriptor, anything but hardcoding these lists!

    protected static Class<?> mapType(Class<?> clazz) {
        if (InputStream.class.isAssignableFrom(clazz)) return File.class;
        if (SAMFileReader.class.isAssignableFrom(clazz)) return File.class;
        if (OutputStream.class.isAssignableFrom(clazz)) return File.class;
        if (GenotypeWriter.class.isAssignableFrom(clazz)) return File.class;
        if (SAMFileWriter.class.isAssignableFrom(clazz)) return File.class;
        if (PlatformUnitFilterHelper.class.isAssignableFrom(clazz)) return String.class;
        return clazz;
    }
}