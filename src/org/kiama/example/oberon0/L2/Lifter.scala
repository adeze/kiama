package org.kiama
package example.oberon0
package L2

/**
 * Lifting transformation for L2.
 */
trait Lifter extends base.Transformer with NameAnalyser {

    import base.source.{Block, Declaration, ModuleDecl}
    import org.kiama.rewriting.Rewriter.{everywherebu, rewrite, rule}
    import scala.collection.mutable.ListBuffer

    /**
     * Lift inner declarations within the module to the top level.  Assumes
     * that identifiers are unique. Then call the next level of transformation.
     */
    override def transform (m : ModuleDecl) : ModuleDecl = {

        /**
         * Buffer of collected declarations.
         */
        val decls = ListBuffer[Declaration] ()

        /**
         * Lift declarations from inner blocks to the top level by adding 
         * them to the declarations buffer in a bottom-up fashion and 
         * removing them from their blocks.
         */
        lazy val liftBlocks =
            everywherebu (
                rule {

                    // Add this block's decls to the buffer, clear them
                    case Block (ds, ss) =>
                        decls appendAll ds
                        Block (Nil, ss)

                    // The module declarations will have been added to the
                    // buffer already. Create a new module with all of the
                    // accumulated declarations.
                    case ModuleDecl (i1, Block (Nil, ss), i2) =>
                        ModuleDecl (i1, Block (decls.result, ss), i2)

                }
            )

        super.transform (rewrite (liftBlocks) (m))

    }

}
