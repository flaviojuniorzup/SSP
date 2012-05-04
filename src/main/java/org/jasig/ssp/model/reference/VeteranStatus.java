package org.jasig.ssp.model.reference;

import java.io.Serializable;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.validation.constraints.NotNull;

/**
 * VeteranStatus reference object.
 */
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class VeteranStatus extends AbstractReference implements Serializable {

	private static final long serialVersionUID = -8572858642333315262L;

	@Column(nullable = false)
	@NotNull
	private short sortOrder = 0;

	/**
	 * Constructor
	 */
	public VeteranStatus() {
		super();
	}

	/**
	 * Constructor
	 * 
	 * @param id
	 *            Identifier; required
	 */

	public VeteranStatus(@NotNull UUID id) {
		super(id);
	}

	/**
	 * Constructor
	 * 
	 * @param id
	 *            Identifier; required
	 * @param name
	 *            Name; required; max 80 characters
	 * @param description
	 *            Description; max 64000 characters
	 * @param sortOrder
	 *            Default sort order when displaying objects to the user
	 */
	public VeteranStatus(@NotNull UUID id, @NotNull String name,
			String description, short sortOrder) {
		super(id, name, description);
		this.sortOrder = sortOrder;
	}

	/**
	 * Gets the default sort order when displaying an item list to the user
	 * 
	 * @return the sortOrder
	 */
	public short getSortOrder() {
		return sortOrder;
	}

	/**
	 * Sets the default sort order when displaying an item list to the user
	 * 
	 * @param sortOrder
	 *            the sortOrder to set
	 */
	public void setSortOrder(short sortOrder) { // NOPMD by jon on 5/4/12 11:16
		this.sortOrder = sortOrder;
	}

	/**
	 * Unique (amongst all Models in the system) prime for use by
	 * {@link #hashCode()}
	 */
	@Override
	protected int hashPrime() {
		return 149;
	};
}
