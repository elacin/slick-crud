package no.penger.crud

trait tableRefs extends tableMetadata with slickIntegration {
  import profile.simple._

  object TableRef{
    /**
     * A reference to a slick table
     *
     * @param table the TableQuery[E] of the table we want to expose
     * @param idCol a function that maps the default projection 'TABLE'
     *              to its primary key column.
     *              Multi-column primary keys are not supported.
     */
    def apply[ID: BaseColumnType: Cell, TABLE <: AbstractTable[_]]
             (mounted:     String,
              table:       TableQuery[TABLE],
              isEditable:  Boolean = true)
             (idCol:       TABLE ⇒ Column[ID])
             (implicit cr: CellRow[TABLE#TableElementType]) = {
      BaseTableRef[ID, TABLE](mounted, table, isEditable, idCol)
    }
  }

  /**
   *  A reference to a slick table

   *  The names of type parameters of this table abstraction are reused within
   *   the whole codebase. Sometimes you will find Q(L)P or O(L)P, in which case
   *   they refer to a query or reference to another table, respectively.
   *
   * @tparam ID the primary key column, for example Column[Int]
   * @tparam TABLE row type for a database reference, ie. the class of the table definition
   * @tparam LP the lifted projection, for example (Column[Int], Column[String])
   * @tparam P the projection, for example (Int, String)
   */
  abstract class TableRef[ID: Cell, TABLE <: AbstractTable[_], LP, P]{
    def base:              BaseTableRef[ID, TABLE]
    def metadata:          Metadata[ID, P]
    def query:             Query[LP, P, Seq]
    def queryById(id: ID): Query[LP, P, Seq]

    def projected[QLP, QP](q: Query[LP, P, Seq] ⇒ Query[QLP, QP, Seq])(implicit cr: CellRow[QP]): TableRef[ID, TABLE, QLP, QP] =
      ProjectedTableRef[ID, TABLE, LP, P, QLP, QP](this, q)

    def filtered[COL: BaseColumnType](on: LP ⇒ Column[COL])(value: COL) =
      FilteredTableRef[ID, TABLE, LP, P, COL](this, on, value)

    private[crud] def extractIdFromRow(row: P) = metadata.extractIdFromRow(row)
  }

  case class BaseTableRef[ID: BaseColumnType: Cell, P <: AbstractTable[_]]
                         (mounted:    String,
                          query:      TableQuery[P],
                          isEditable: Boolean,
                          idCol:      P ⇒ Column[ID])
                         (implicit cr: CellRow[P#TableElementType]) extends TableRef[ID, P, P, P#TableElementType]{
    override val metadata          = Metadata.infer(query, idCol)
    override val base              = this
    override def queryById(id: ID) = query.filter(row ⇒ idCol(row) === id)
    val tableName:  TableName  = AstParser.tableName(query)
    val primaryKey: ColumnName = metadata.idColName
    val idCell:     Cell[ID]   = metadata.idCell
  }

  case class ProjectedTableRef[ID: Cell, TABLE <: AbstractTable[_], LP, P, QLP, QP: CellRow]
                              (wrapped: TableRef[ID, TABLE, LP, P],
                               proj:    Query[LP, P, Seq] ⇒ Query[QLP, QP, Seq]) extends TableRef[ID, TABLE, QLP, QP]{

    override val base               = wrapped.base
    override val query              = proj(wrapped.query)
    override def queryById(id: ID)  = proj(wrapped.queryById(id))
    override def metadata           = Metadata.derive(query, wrapped.metadata)
  }

  case class FilteredTableRef[ID: Cell, TABLE <: AbstractTable[_], LP, P, COL: BaseColumnType]
                             (wrapped:   TableRef[ID, TABLE, LP, P],
                              columnFor: LP ⇒ Column[COL],
                              colValue:  COL) extends TableRef[ID, TABLE, LP, P]{

    def filter(q: Query[LP, P, Seq]) = q.filter(columnFor(_) === colValue)

    val filterColumn                = AstParser.colNames(wrapped.query.map(columnFor)).head
    override val base               = wrapped.base
    override val metadata           = wrapped.metadata
    override val query              = filter(wrapped.query)
    override def queryById(id: ID)  = filter(wrapped.queryById(id))
  }
}
