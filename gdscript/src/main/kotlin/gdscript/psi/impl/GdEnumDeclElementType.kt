package gdscript.psi.impl

import com.intellij.credentialStore.createSecureRandom
import com.intellij.psi.stubs.*
import com.jetbrains.rider.godot.community.gdscript.GdLanguage
import gdscript.index.Indices
import gdscript.index.stub.GdEnumDeclStub
import gdscript.index.stub.GdEnumDeclStubImpl
import gdscript.model.GdCommentModel
import gdscript.psi.GdEnumDeclTl
import gdscript.psi.utils.GdCommentUtil
import gdscript.psi.utils.PsiGdEnumUtil

/**
 * Represents the ElementType for Enum Declarations in the Godot GDScript specific PSI tree.
 * Handles creating and managing PSI and stub elements for enum declarations.
 */
object GdEnumDeclElementType : IStubElementType<GdEnumDeclStub, GdEnumDeclTl>("enumDecl", GdLanguage) {

    /**
     * Retrieves an instance of the GdEnumDeclElementType.
     *
     * @param debugName the name used for debugging purposes. This parameter is not used in the current implementation.
     * @return an instance of GdEnumDeclElementType.
     */
    @JvmStatic
    fun getInstance(@Suppress("UNUSED_PARAMETER") debugName: String): GdEnumDeclElementType {
        return GdEnumDeclElementType
    }


    /**
     * Retrieves the external identifier associated with this element.
     *
     * @return the external identifier as a string, specifically "GdScript.enumDecl".
     */
    override fun getExternalId(): String = "GdScript.enumDecl"


    /**
     * Serializes the given `GdEnumDeclStub` object into the provided `StubOutputStream`.
     *
     * @param stub The `GdEnumDeclStub` instance containing the data to be serialized.
     * @param dataStream The `StubOutputStream` into which the data of the `GdEnumDeclStub` will be serialized.
     */
    override fun serialize(stub: GdEnumDeclStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name())
        dataStream.writeName(stub.values().toString())
        GdCommentModel.serializeDocumentation(stub, dataStream)
    }


    /**
     * Deserializes a stream of data into a `GdEnumDeclStub` object.
     *
     * @param dataStream the input stream of data being deserialized.
     * @param parentStub the parent stub element associated with the deserialized stub, or null if there is no parent.
     * @return an instance of `GdEnumDeclStub` constructed from the provided data.
     */
    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): GdEnumDeclStub =
        GdEnumDeclStubImpl(
            parentStub,
            dataStream.readNameString(),
            PsiGdEnumUtil.fromString(dataStream.readNameString()),
            GdCommentModel(dataStream),
        )


    /**
     * Indexes the given GdEnumDeclStub to populate search indices.
     *
     * The method ensures the enum declaration is indexed with a relevant name. If the name provided
     * by the stub is null or empty, the first key from the stub's value map is used. If no names are
     * available, a random secure identifier is generated as a fallback.
     *
     * @param stub The GdEnumDeclStub object representing the enum declaration to be indexed.
     * @param sink The IndexSink instance used to store indexing occurrences.
     */
    override fun indexStub(stub: GdEnumDeclStub, sink: IndexSink) {
        var name: String? = stub.name()
        // TODO losos
        if ((name == null) || name.isEmpty()) {
            name = stub.values().keys.firstOrNull()
        }
        if ((name == null) || name.isEmpty()) {
            name = createSecureRandom().toString()
        }

        sink.occurrence(Indices.ENUM, name)
    }


    /**
     * Creates a PSI (Program Structure Interface) representation of the provided enum declaration stub.
     *
     * @param stub The `GdEnumDeclStub` instance containing the structural information
     *             about the enum declaration.
     * @return A `GdEnumDeclTl` instance that represents the PSI element of the enum declaration.
     */
    override fun createPsi(stub: GdEnumDeclStub): GdEnumDeclTl =
        GdEnumDeclTlImpl(stub, stub.stubType)


    /**
     * Creates a stub for the specified enum declaration PSI element.
     *
     * @param psi The PSI element representing the enum declaration.
     * @param parentStub The parent stub element, or null if there is no parent stub.
     * @return A new instance of GdEnumDeclStub generated based on the provided PSI element.
     */
    override fun createStub(psi: GdEnumDeclTl, parentStub: StubElement<*>?): GdEnumDeclStub {
        return GdEnumDeclStubImpl(
            parentStub, psi.enumDeclNmi?.name, psi.values,
            GdCommentUtil.collectComments(psi),
        )
    }

}
