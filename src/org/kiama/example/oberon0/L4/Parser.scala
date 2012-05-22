package org.kiama
package example.oberon0
package L4

/**
 * Parsers for L4 language.
 */
trait Parser extends L3.Parser {

    import source.{ArrayTypeDef, FieldExp, FieldIdn, FieldList, IndexExp,
        RecordTypeDef}

    override def typedefDef =
        ("ARRAY" ~> expression) ~ ("OF" ~> typedef) ^^ ArrayTypeDef |
        "RECORD" ~> fieldlists <~ "END" ^^ RecordTypeDef |
        super.typedefDef

    lazy val fieldlists =
        rep1sep (fieldlist, ";") ^^ (_.flatten)

    lazy val fieldlist =
        (idnlist <~ ":") ~ typedef ^^ {
            case is ~ t => Some (FieldList (is, t))
        } |
        result (None)

    lazy val idnlist =
        rep1sep (ident, ",")

    override def lhsDef =
        lhs ~ ("." ~> fldidn) ^^ FieldExp |
        lhs ~ ("[" ~> expression <~ "]") ^^ IndexExp |
        super.lhsDef

    lazy val fldidn =
        ident ^^ FieldIdn

    override def keywords =
        "ARRAY" :: "OF" :: "RECORD" :: super.keywords

}
