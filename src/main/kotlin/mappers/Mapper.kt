package mappers

interface Mapper {

    // Functions to adjust the given program and character address according to the mappers specs
    fun adjustProgramAddress(address: Int): Int
    fun adjustCharacterAddress(address: Int): Int
}