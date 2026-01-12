package gdscript.psi.impl

import com.intellij.psi.stubs.*
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.rider.godot.community.gdscript.GdLanguage
import gdscript.index.Indices
import gdscript.index.stub.GdClassIdStub
import gdscript.index.stub.GdClassIdStubImpl
import gdscript.psi.*
import gdscript.psi.utils.PsiGdClassUtil
import gdscript.psi.utils.PsiGdTreeUtil
import gdscript.utils.VirtualFileUtil.resourcePath

/**
 * Represents the element type definition for GdScript class identifiers.
 * Provides methods to interact with class ID elements and their respective stubs.
 */
object GdClassIdElementType : IStubElementType<GdClassIdStub, GdClassNameNmi>("gd.classId", GdLanguage) {

    /**
     * Constructs a fully qualified class ID by traversing the parent hierarchy of the given class element.
     *
     * @param element The class element for which the class ID is to be determined.
     * @return A string representing the fully qualified class ID, constructed using parent class names
     *         or file resource paths, in a dot-separated format.
     */
    fun getClassId(element: GdClassNameNmi): String {
        val parents: MutableList<String> = mutableListOf()
        var parent = PsiGdClassUtil.getParentClassElement(element)

        while (true) {
            if (parent is GdClassDeclTl) {
                parents.add(parent.classNameNmi?.name.orEmpty())
            } else if (parent is GdClassNaming) {
                parents.add(parent.classNameNmi?.name.orEmpty())
                break
            } else if (parent is GdFile) {
                parents.add("\"${parent.virtualFile ?: parent.originalFile.virtualFile.resourcePath()}\"")
                break
            } else {
                break
            }

            parent = PsiGdClassUtil.getParentClassElement(parent)
        }

        return parents.reversed().joinToString(".")
    }


    /**
     * Retrieves the name of the parent class for the given `GdClassNameNmi` element.
     *
     * @param element the `GdClassNameNmi` element whose parent's name is to be retrieved
     * @return the name of the parent class as a `String`, or `null` if no parent class is found
     */
    @Deprecated("this should be at declaration, not here.. ?")
    fun getParentName(element: GdClassNameNmi): String? {
        val declaration = PsiGdTreeUtil.findFirstPrecedingElement(element) {
            (it is GdClassDeclTl) || (it is GdClassNaming)
        }

        val inh = PsiTreeUtil.findChildOfType(declaration, GdInheritance::class.java)

        return inh?.inheritancePath
    }


    /**
     * Retrieves an instance of the GdClassIdElementType.
     *
     * @param debugName A string parameter, typically used for debugging or distinguishing element types.
     * @return The singleton instance of GdClassIdElementType.
     */
    @JvmStatic
    fun getInstance(@Suppress("UNUSED_PARAMETER") debugName: String): GdClassIdElementType {
        return GdClassIdElementType
    }


    /**
     * Retrieves the external identifier associated with this element.
     *
     * @return the external identifier string.
     */
    override fun getExternalId(): String = "GdScript.classId"


    /**
     * Serializes a GdClassIdStub instance to the specified data stream.
     *
     * @param stub The GdClassIdStub instance containing the data to be serialized.
     * @param dataStream The output stream where the serialized data will be written.
     */
    override fun serialize(stub: GdClassIdStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name())
        dataStream.writeName(stub.parent())
    }


    /**
     * Deserializes data from the given stream to create a GdClassIdStub instance.
     *
     * @param dataStream the stream from which the data is read during deserialization
     * @param parentStub the parent stub element to which the created stub will be linked, or null if it has no parent
     * @return a new instance of GdClassIdStub populated with the deserialized data
     */
    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): GdClassIdStub {
        return GdClassIdStubImpl(parentStub, dataStream.readNameString()!!, dataStream.readNameString())
    }


    /**
     * Indexes a given stub for occurrences of class names.
     *
     * @param stub the `GdClassIdStub` representing a class identifier stub to be indexed
     * @param sink the `IndexSink` used to index occurrences
     */
    override fun indexStub(stub: GdClassIdStub, sink: IndexSink) {
        sink.occurrence(Indices.CLASS_NAME_ID, stub.name())
    }


    /**
     * Creates a PSI element for the given GdClassIdStub.
     *
     * @param stub the stub object that contains the necessary data for creating the PSI element
     * @return the created PSI element of type GdClassNameNmi
     */
    override fun createPsi(stub: GdClassIdStub): GdClassNameNmi =
        GdClassNameNmiImpl(stub, stub.stubType)


    /**
     * Creates a new stub for the provided PSI element representing a class name.
     *
     * @param psi the PSI element representing the class name for which the stub is being created
     * @param parentStub the parent stub of the newly created stub, or null if it has no parent
     * @return a new instance of GdClassIdStub representing the specified class and its hierarchy information
     */
    override fun createStub(psi: GdClassNameNmi, parentStub: StubElement<*>?): GdClassIdStub {
        return GdClassIdStubImpl(parentStub, psi.classId, getParentName(psi))
    }

}
