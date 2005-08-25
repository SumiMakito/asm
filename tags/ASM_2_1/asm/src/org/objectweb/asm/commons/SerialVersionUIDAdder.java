/***
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2005 INRIA, France Telecom
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
package org.objectweb.asm.commons;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A {@link ClassAdapter} that adds a serial version unique identifier to a
 * class if missing. The SVUID algorithm can be found <a href=
 * "http://java.sun.com/j2se/1.4.2/docs/guide/serialization/spec/class.html"
 * >http://java.sun.com/j2se/1.4.2/docs/guide/serialization/spec/class.html</a>:
 * 
 * <pre>
 * The serialVersionUID is computed using the signature of a stream of bytes
 * that reflect the class definition. The National Institute of Standards and
 * Technology (NIST) Secure Hash Algorithm (SHA-1) is used to compute a
 * signature for the stream. The first two 32-bit quantities are used to form a
 * 64-bit hash. A java.lang.DataOutputStream is used to convert primitive data
 * types to a sequence of bytes. The values input to the stream are defined by
 * the Java Virtual Machine (VM) specification for classes.
 *
 * The sequence of items in the stream is as follows:
 *
 * 1. The class name written using UTF encoding.
 * 2. The class modifiers written as a 32-bit integer.
 * 3. The name of each interface sorted by name written using UTF encoding.
 * 4. For each field of the class sorted by field name (except private static
 * and private transient fields):
 * 1. The name of the field in UTF encoding.
 * 2. The modifiers of the field written as a 32-bit integer.
 * 3. The descriptor of the field in UTF encoding
 * 5. If a class initializer exists, write out the following:
 * 1. The name of the method, &lt;clinit&gt;, in UTF encoding.
 * 2. The modifier of the method, java.lang.reflect.Modifier.STATIC,
 * written as a 32-bit integer.
 * 3. The descriptor of the method, ()V, in UTF encoding.
 * 6. For each non-private constructor sorted by method name and signature:
 * 1. The name of the method, &lt;init&gt;, in UTF encoding.
 * 2. The modifiers of the method written as a 32-bit integer.
 * 3. The descriptor of the method in UTF encoding.
 * 7. For each non-private method sorted by method name and signature:
 * 1. The name of the method in UTF encoding.
 * 2. The modifiers of the method written as a 32-bit integer.
 * 3. The descriptor of the method in UTF encoding.
 * 8. The SHA-1 algorithm is executed on the stream of bytes produced by
 * DataOutputStream and produces five 32-bit values sha[0..4].
 *
 * 9. The hash value is assembled from the first and second 32-bit values of 
 * the SHA-1 message digest. If the result of the message digest, the five
 * 32-bit words H0 H1 H2 H3 H4, is in an array of five int values named 
 * sha, the hash value would be computed as follows:
 *
 * long hash = ((sha[0] &gt;&gt;&gt; 24) &amp; 0xFF) |
 * ((sha[0] &gt;&gt;&gt; 16) &amp; 0xFF) &lt;&lt; 8 |
 * ((sha[0] &gt;&gt;&gt; 8) &amp; 0xFF) &lt;&lt; 16 |
 * ((sha[0] &gt;&gt;&gt; 0) &amp; 0xFF) &lt;&lt; 24 |
 * ((sha[1] &gt;&gt;&gt; 24) &amp; 0xFF) &lt;&lt; 32 |
 * ((sha[1] &gt;&gt;&gt; 16) &amp; 0xFF) &lt;&lt; 40 |
 * ((sha[1] &gt;&gt;&gt; 8) &amp; 0xFF) &lt;&lt; 48 |
 * ((sha[1] &gt;&gt;&gt; 0) &amp; 0xFF) &lt;&lt; 56;
 * </pre>
 * 
 * @author Rajendra Inamdar, Vishal Vishnoi
 */
public class SerialVersionUIDAdder extends ClassAdapter {

    /**
     * Flag that indicates if we need to compute SVUID.
     */
    protected boolean computeSVUID;

    /**
     * Set to true if the class already has SVUID.
     */
    protected boolean hasSVUID;

    /**
     * Classes access flags.
     */
    protected int access;

    /**
     * Internal name of the class
     */
    protected String name;

    /**
     * Interfaces implemented by the class.
     */
    protected String[] interfaces;

    /**
     * Collection of fields. (except private static and private transient
     * fields)
     */
    protected Collection svuidFields;

    /**
     * Set to true if the class has static initializer.
     */
    protected boolean hasStaticInitializer;

    /**
     * Collection of non-private constructors.
     */
    protected Collection svuidConstructors;

    /**
     * Collection of non-private methods.
     */
    protected Collection svuidMethods;

    /**
     * Creates a new {@link SerialVersionUIDAdder}.
     * 
     * @param cv a {@link ClassVisitor} to which this visitor will delegate
     *        calls.
     */
    public SerialVersionUIDAdder(final ClassVisitor cv) {
        super(cv);
        svuidFields = new ArrayList();
        svuidConstructors = new ArrayList();
        svuidMethods = new ArrayList();
    }

    // ------------------------------------------------------------------------
    // Overriden methods
    // ------------------------------------------------------------------------

    /*
     * Visit class header and get class name, access , and intefraces
     * informatoin (step 1,2, and 3) for SVUID computation.
     */
    public void visit(
        final int version,
        final int access,
        final String name,
        final String signature,
        final String superName,
        final String[] interfaces)
    {
        computeSVUID = (access & Opcodes.ACC_INTERFACE) == 0;

        if (computeSVUID) {
            this.name = name;
            this.access = access;
            this.interfaces = interfaces;
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    /*
     * Visit the methods and get constructor and method information (step 5 and
     * 7). Also determince if there is a class initializer (step 6).
     */
    public MethodVisitor visitMethod(
        final int access,
        final String name,
        final String desc,
        final String signature,
        final String[] exceptions)
    {
        if (computeSVUID) {
            if (name.equals("<clinit>")) {
                hasStaticInitializer = true;
            }
            /*
             * Remembers non private constructors and methods for SVUID
             * computation For constructor and method modifiers, only the
             * ACC_PUBLIC, ACC_PRIVATE, ACC_PROTECTED, ACC_STATIC, ACC_FINAL,
             * ACC_SYNCHRONIZED, ACC_NATIVE, ACC_ABSTRACT and ACC_STRICT flags
             * are used.
             */
            int mods = access
                    & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE
                            | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC
                            | Opcodes.ACC_FINAL | Opcodes.ACC_SYNCHRONIZED
                            | Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_STRICT);

            // all non private methods
            if ((access & Opcodes.ACC_PRIVATE) == 0) {
                if (name.equals("<init>")) {
                    svuidConstructors.add(new Item(name, mods, desc));
                } else if (!name.equals("<clinit>")) {
                    svuidMethods.add(new Item(name, mods, desc));
                }
            }
        }

        return cv.visitMethod(access, name, desc, signature, exceptions);
    }

    /*
     * Gets class field information for step 4 of the alogrithm. Also determines
     * if the class already has a SVUID.
     */
    public FieldVisitor visitField(
        final int access,
        final String name,
        final String desc,
        final String signature,
        final Object value)
    {
        if (computeSVUID) {
            if (name.equals("serialVersionUID")) {
                // since the class already has SVUID, we won't be computing it.
                computeSVUID = false;
                hasSVUID = true;
            }
            /*
             * Remember field for SVUID computation For field modifiers, only
             * the ACC_PUBLIC, ACC_PRIVATE, ACC_PROTECTED, ACC_STATIC,
             * ACC_FINAL, ACC_VOLATILE, and ACC_TRANSIENT flags are used when
             * computing serialVersionUID values.
             */
            int mods = access
                    & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE
                            | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC
                            | Opcodes.ACC_FINAL | Opcodes.ACC_VOLATILE | Opcodes.ACC_TRANSIENT);

            if (((access & Opcodes.ACC_PRIVATE) == 0)
                    || ((access & (Opcodes.ACC_STATIC | Opcodes.ACC_TRANSIENT)) == 0))
            {
                svuidFields.add(new Item(name, mods, desc));
            }
        }

        return super.visitField(access, name, desc, signature, value);
    }

    /*
     * Add the SVUID if class doesn't have one
     */
    public void visitEnd() {
        // compute SVUID and add it to the class
        if (computeSVUID && !hasSVUID) {
            try {
                cv.visitField(Opcodes.ACC_FINAL + Opcodes.ACC_STATIC,
                        "serialVersionUID",
                        "J",
                        null,
                        new Long(computeSVUID()));
            } catch (Throwable e) {
                throw new RuntimeException("Error while computing SVUID for "
                        + name, e);
            }
        }

        super.visitEnd();
    }

    // ------------------------------------------------------------------------
    // Utility methods
    // ------------------------------------------------------------------------

    /**
     * Returns the value of SVUID if the class doesn't have one already. Please
     * note that 0 is returned if the class already has SVUID, thus use
     * <code>isHasSVUID</code> to determine if the class already had an SVUID.
     * 
     * @return Returns the serial version UID
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    protected long computeSVUID() throws IOException, NoSuchAlgorithmException {
        if (hasSVUID) {
            return 0;
        }

        ByteArrayOutputStream bos = null;
        DataOutputStream dos = null;
        long svuid = 0;

        try {
            bos = new ByteArrayOutputStream();
            dos = new DataOutputStream(bos);

            /*
             * 1. The class name written using UTF encoding.
             */
            dos.writeUTF(name.replace('/', '.'));

            /*
             * 2. The class modifiers written as a 32-bit integer.
             */
            dos.writeInt(access
                    & (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL
                            | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT));

            /*
             * 3. The name of each interface sorted by name written using UTF
             * encoding.
             */
            Arrays.sort(interfaces);
            for (int i = 0; i < interfaces.length; i++) {
                dos.writeUTF(interfaces[i].replace('/', '.'));
            }

            /*
             * 4. For each field of the class sorted by field name (except
             * private static and private transient fields):
             * 
             * 1. The name of the field in UTF encoding. 2. The modifiers of the
             * field written as a 32-bit integer. 3. The descriptor of the field
             * in UTF encoding
             * 
             * Note that field signatutes are not dot separated. Method and
             * constructor signatures are dot separated. Go figure...
             */
            writeItems(svuidFields, dos, false);

            /*
             * 5. If a class initializer exists, write out the following: 1. The
             * name of the method, <clinit>, in UTF encoding. 2. The modifier of
             * the method, java.lang.reflect.Modifier.STATIC, written as a
             * 32-bit integer. 3. The descriptor of the method, ()V, in UTF
             * encoding.
             */
            if (hasStaticInitializer) {
                dos.writeUTF("<clinit>");
                dos.writeInt(Opcodes.ACC_STATIC);
                dos.writeUTF("()V");
            } // if..

            /*
             * 6. For each non-private constructor sorted by method name and
             * signature: 1. The name of the method, <init>, in UTF encoding. 2.
             * The modifiers of the method written as a 32-bit integer. 3. The
             * descriptor of the method in UTF encoding.
             */
            writeItems(svuidConstructors, dos, true);

            /*
             * 7. For each non-private method sorted by method name and
             * signature: 1. The name of the method in UTF encoding. 2. The
             * modifiers of the method written as a 32-bit integer. 3. The
             * descriptor of the method in UTF encoding.
             */
            writeItems(svuidMethods, dos, true);

            dos.flush();

            /*
             * 8. The SHA-1 algorithm is executed on the stream of bytes
             * produced by DataOutputStream and produces five 32-bit values
             * sha[0..4].
             */
            MessageDigest md = MessageDigest.getInstance("SHA");

            /*
             * 9. The hash value is assembled from the first and second 32-bit
             * values of the SHA-1 message digest. If the result of the message
             * digest, the five 32-bit words H0 H1 H2 H3 H4, is in an array of
             * five int values named sha, the hash value would be computed as
             * follows:
             * 
             * long hash = ((sha[0] >>> 24) & 0xFF) | ((sha[0] >>> 16) & 0xFF) <<
             * 8 | ((sha[0] >>> 8) & 0xFF) << 16 | ((sha[0] >>> 0) & 0xFF) <<
             * 24 | ((sha[1] >>> 24) & 0xFF) << 32 | ((sha[1] >>> 16) & 0xFF) <<
             * 40 | ((sha[1] >>> 8) & 0xFF) << 48 | ((sha[1] >>> 0) & 0xFF) <<
             * 56;
             */
            byte[] hashBytes = md.digest(bos.toByteArray());
            for (int i = Math.min(hashBytes.length, 8) - 1; i >= 0; i--) {
                svuid = (svuid << 8) | (hashBytes[i] & 0xFF);
            }
        } finally {
            // close the stream (if open)
            if (dos != null) {
                dos.close();
            }
        }

        return svuid;
    }

    /**
     * Sorts the items in the collection and writes it to the data output stream
     * 
     * @param itemCollection collection of items
     * @param dos a <code>DataOutputStream</code> value
     * @param dotted a <code>boolean</code> value
     * @exception IOException if an error occurs
     */
    private void writeItems(
        final Collection itemCollection,
        final DataOutputStream dos,
        final boolean dotted) throws IOException
    {
        int size = itemCollection.size();
        Item items[] = (Item[]) itemCollection.toArray(new Item[size]);
        Arrays.sort(items);
        for (int i = 0; i < size; i++) {
            dos.writeUTF(items[i].name);
            dos.writeInt(items[i].access);
            dos.writeUTF(dotted
                    ? items[i].desc.replace('/', '.')
                    : items[i].desc);
        }
    }

    // ------------------------------------------------------------------------
    // Inner classes
    // ------------------------------------------------------------------------

    static class Item implements Comparable {

        String name;

        int access;

        String desc;

        Item(final String name, final int access, final String desc) {
            this.name = name;
            this.access = access;
            this.desc = desc;
        }

        public int compareTo(final Object o) {
            Item other = (Item) o;
            int retVal = name.compareTo(other.name);
            if (retVal == 0) {
                retVal = desc.compareTo(other.desc);
            }
            return retVal;
        }
    }
}
