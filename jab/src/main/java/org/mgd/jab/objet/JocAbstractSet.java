package org.mgd.jab.objet;

import org.mgd.jab.dto.Dto;

import java.util.AbstractSet;
import java.util.Set;

/**
 * Classe abstraite à utiliser pour créer des implementations de {@link Set} d'objet métier de type {@link Jo}
 * qui permet notamment la gestion des parents d'un objet métier.
 *
 * @param <T> le type des éléments de la collection
 * @param <C> le type de la collection
 * @author Maxime
 */
public abstract class JocAbstractSet<T, C extends AbstractSet<T>> extends JocAbstractCollection<T, C> implements Set<T> {
    protected JocAbstractSet(Jo<? extends Dto> contenant) {
        super(contenant);
    }
}
