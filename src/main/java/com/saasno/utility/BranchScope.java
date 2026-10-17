package com.saasno.utility;

import java.util.HashSet;
import java.util.Set;

import com.saasno.utility.model.data.DataFilter;

// Cakupan cabang yang boleh diakses: cabang sendiri + seluruh turunannya. Global (tanpa cabang)
// berarti tidak dibatasi; dipakai untuk super user yang tidak ditempatkan di cabang mana pun.
public final class BranchScope {

	private static final BranchScope GLOBAL = new BranchScope(null, null, null);

	private final String branchId;
	private final Set<String> ids;
	// Cabang ini beserta induk-induknya, plus seluruh turunannya: dipakai untuk data yang boleh dibaca
	// turun dari induk (produk) tanpa boleh diubah.
	private final Set<String> visibleIds;

	private BranchScope(String branchId, Set<String> ids, Set<String> visibleIds) {
		this.branchId = branchId;
		this.ids = ids;
		this.visibleIds = visibleIds;
	}

	public static BranchScope global() {
		return GLOBAL;
	}

	// ancestorIds: cabang ini dan induk-induknya.
	public static BranchScope of(String branchId, Set<String> ids, Set<String> ancestorIds) {
		Set<String> visible = new HashSet<>(ids);
		visible.addAll(ancestorIds);
		return new BranchScope(branchId, Set.copyOf(ids), Set.copyOf(visible));
	}

	public boolean isGlobal() {
		return ids == null;
	}

	// Cabang tempat user berada; null untuk global.
	public String getBranchId() {
		return branchId;
	}

	// Cabang yang dicakup; null untuk global (tidak dibatasi).
	public Set<String> getIds() {
		return ids;
	}

	// Cabang null (data tanpa cabang) hanya dicakup oleh scope global.
	public boolean contains(String id) {
		return isGlobal() || (id != null && ids.contains(id));
	}

	// Data milik cabang ini boleh dibaca: milik cabang sendiri, turunan, atau induk.
	public boolean canSee(String id) {
		return isGlobal() || (id != null && visibleIds.contains(id));
	}

	// Cabang yang datanya boleh dibaca; null untuk global.
	public Set<String> getVisibleIds() {
		return visibleIds;
	}

	public DataFilter applyVisible(DataFilter filter, String field) {
		return isGlobal() ? filter : filter.restrict(field, visibleIds);
	}

	public DataFilter apply(DataFilter filter, String field) {
		return isGlobal() ? filter : filter.restrict(field, ids);
	}

}
