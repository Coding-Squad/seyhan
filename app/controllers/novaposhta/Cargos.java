/**
* Copyright (c) 2015 Mustafa DUMLUPINAR, mdumlupinar@gmail.com
*
* This file is part of seyhan project.
*
* seyhan is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*/
package controllers.novaposhta;

import static play.data.Form.form;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import meta.GridHeader;
import meta.PageExtend;
import models.NovaposhtaCargo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.data.Form;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AuthManager;
import utils.CacheUtils;
import utils.StringUtils;
import views.html.novaposhtas.cargo.form;
import views.html.novaposhtas.cargo.index;
import views.html.novaposhtas.cargo.list;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.SqlQuery;
import com.avaje.ebean.SqlRow;

import controllers.Application;
import controllers.global.Profiles;
import enums.Right;
import enums.RightLevel;

/**
 * @author mdpinar
*/
public class Cargos extends Controller {

	private final static Right RIGHT_SCOPE = Right.NOVAPOSHTA_KARGO_TANITIMI;

	private final static Logger log = LoggerFactory.getLogger(Cargos.class);
	private final static Form<NovaposhtaCargo> dataForm = form(NovaposhtaCargo.class);

	private static String lastSaved;

	/**
	 * Liste formu basliklarini doner
	 * 
	 * @return List<GridHeader>
	 */
	private static List<GridHeader> getHeaderList() {
		List<GridHeader> headerList = new ArrayList<GridHeader>();
		headerList.add(new GridHeader(Messages.get("name"), true).sortable("name"));
		headerList.add(new GridHeader(Messages.get("responsible"), "20%"));
		headerList.add(new GridHeader(Messages.get("phoneX", "1"), "10%"));
		headerList.add(new GridHeader(Messages.get("phoneX", "2"), "10%"));
		headerList.add(new GridHeader(Messages.get("is_active"), "7%", true));

		return headerList;
	}

	/**
	 * Liste formunda gosterilecek verileri doner
	 * 
	 * @return PageExtend
	 */
	private static PageExtend<NovaposhtaCargo> buildPage() {
		List<Map<Integer, String>> dataList = new ArrayList<Map<Integer, String>>();

		List<NovaposhtaCargo> modelList = NovaposhtaCargo.page();
		if (modelList != null && modelList.size() > 0) {
			for (NovaposhtaCargo model : modelList) {
				Map<Integer, String> dataMap = new HashMap<Integer, String>();
				int i = -1;
				dataMap.put(i++, model.id.toString());
				dataMap.put(i++, model.name);
				dataMap.put(i++, model.responsible);
				dataMap.put(i++, model.phone1);
				dataMap.put(i++, model.phone2);
				dataMap.put(i++, model.isActive.toString());

				dataList.add(dataMap);
			}
		}

		return new PageExtend<NovaposhtaCargo>(getHeaderList(), dataList, null);
	}

	public static Result index() {
		Result hasProblem = AuthManager.hasProblem(RIGHT_SCOPE, RightLevel.Enable);
		if (hasProblem != null) return hasProblem;

		return ok(
			index.render(buildPage())
		);
	}

	public static Result options(boolean hasBlankOption) {
		Result result = ok(StringUtils.buildOptionTag(NovaposhtaCargo.options(), lastSaved, hasBlankOption));
		lastSaved = null;

		return result;
	}

	/**
	 * Uzerinde veri bulunan liste formunu doner
	 */
	public static Result list() {
		Result hasProblem = AuthManager.hasProblem(RIGHT_SCOPE, RightLevel.Enable);
		if (hasProblem != null) return hasProblem;

		return ok(
			list.render(buildPage())
		);
	}

	/**
	 * Kayit formundaki bilgileri kaydeder
	 */
	public static Result save() {
		if (! CacheUtils.isLoggedIn()) return Application.login();

		Form<NovaposhtaCargo> filledForm = dataForm.bindFromRequest();

		if(filledForm.hasErrors()) {
			return badRequest(form.render(filledForm));
		} else {

			NovaposhtaCargo model = filledForm.get();

			Result hasProblem = AuthManager.hasProblem(RIGHT_SCOPE, (model.id == null ? RightLevel.Insert : RightLevel.Update));
			if (hasProblem != null) return hasProblem;

			String editingConstraintError = model.checkEditingConstraints();
			if (editingConstraintError != null) return badRequest(editingConstraintError);

			checkConstraints(filledForm);

			if (filledForm.hasErrors()) {
				return badRequest(form.render(filledForm));
			}

			try {
				if (model.id == null) {
					model.save();
				} else {
					model.update();
				}
			} catch (OptimisticLockException e) {
				flash("error", Messages.get("exception.optimistic.lock"));
				return badRequest(form.render(dataForm.fill(model)));
			}
			lastSaved = model.name;

			flash("success", Messages.get("saved", model.name));
			if (Profiles.chosen().gnel_continuouslyRecording)
				return create();
			else
				return ok();
		}
	}

	/**
	 * Yeni bir kayit formu olusturur
	 */
	public static Result create() {
		Result hasProblem = AuthManager.hasProblem(RIGHT_SCOPE, RightLevel.Insert);
		if (hasProblem != null) return hasProblem;

		return ok(form.render(dataForm.fill(new NovaposhtaCargo())));
	}

	/**
	 * Secilen kayit icin duzenleme formunu acar
	 * 
	 * @param id
	 */
	public static Result edit(Integer id) {
		Result hasProblem = AuthManager.hasProblem(RIGHT_SCOPE, RightLevel.Enable);
		if (hasProblem != null) return hasProblem;

		if (id == null) {
			return badRequest(Messages.get("id.is.null"));
		} else {
			NovaposhtaCargo model = NovaposhtaCargo.findById(id);
			if (model == null) {
				return badRequest(Messages.get("not.found", Messages.get("novaposhta.cargo.company")));
			} else {
				return ok(form.render(dataForm.fill(model)));
			}
		}
	}

	/**
	 * Duzenlemek icin acilmis olan kaydi siler
	 * 
	 * @param id
	 */
	public static Result remove(Integer id) {
		Result hasProblem = AuthManager.hasProblem(RIGHT_SCOPE, RightLevel.Delete);
		if (hasProblem != null) return hasProblem;

		if (id == null) {
			return badRequest(Messages.get("id.is.null"));
		} else {
			NovaposhtaCargo model = NovaposhtaCargo.findById(id);
			if (model == null) {
				return badRequest(Messages.get("not.found", Messages.get("novaposhta.cargo.company")));
			} else {
				String editingConstraintError = model.checkEditingConstraints();
				if (editingConstraintError != null) return badRequest(editingConstraintError);
				try {
					model.delete();
					flash("success", Messages.get("deleted", model.name));
					return ok();
				} catch (PersistenceException pe) {
					flash("error", Messages.get("delete.violation", model.name));
					log.error("ERROR", pe);
					return badRequest(Messages.get("delete.violation", model.name));
				}
			}
		}
	}

	public static double findBalance(Integer cargoId, Date date) {
		StringBuilder querySB = new StringBuilder();
		querySB.append("select sum(carg_value) as sumCargoValue, sum(money) as sumMoney, sum(_return) as sumReturn from novaposhta_cargo_trans ");
		querySB.append("where workspace = :workspace ");
		querySB.append("  and cargo_id = :cargo_id ");
		if (date != null) {
			querySB.append("  and trans_date < :trans_date");
		}
		querySB.append(" group by workspace ");

		SqlQuery query = Ebean.createSqlQuery(querySB.toString());
		query.setParameter("workspace", CacheUtils.getWorkspaceId());
		query.setParameter("cargo_id", cargoId);
		query.setParameter("trans_date", date);
		SqlRow row = query.findUnique();

		double result = 0;
		if (row != null && row.size() > 0) {
			if (row.getDouble("sumCargoValue") != null) result += row.getDouble("sumCargoValue").doubleValue();
			if (row.getDouble("sumMoney") != null) result -= row.getDouble("sumMoney").doubleValue();
			if (row.getDouble("sumReturn") != null) result -= row.getDouble("sumReturn").doubleValue();
		}

		return result;
	}

	/**
	 * Kayit isleminden once form uzerinde bulunan verilerin uygunlugunu kontrol eder
	 * 
	 * @param filledForm
	 */
	private static void checkConstraints(Form<NovaposhtaCargo> filledForm) {
		NovaposhtaCargo model = filledForm.get();

		if (NovaposhtaCargo.isUsedForElse("name", model.name, model.id)) {
			filledForm.reject("name", Messages.get("not.unique", model.name));
		}
	}

}
