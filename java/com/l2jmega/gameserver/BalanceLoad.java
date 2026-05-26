/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.l2jmega.gameserver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import com.l2jmega.L2DatabaseFactory;

/**
 * @author L2FOl
 */
public class BalanceLoad
{
	private static volatile boolean _balanceSchemaChecked = false;
	private static volatile boolean _balanceSchemaValid = false;
	
	public static int[] Evasion = new int[31], Accuracy = new int[31], PAtk = new int[31],
			MAtk = new int[31], PDef = new int[31], MDef = new int[31], HP = new int[31],
			MP = new int[31], MAtkSpd = new int[31], PAtkSpd = new int[31];

	public static void LoadEm()
	{
		if (!isBalanceSchemaValid())
		{
			return;
		}
		
		int z;

		for (z = 0; z < 31; z++)
		{
			Evasion[z] = loadEvasion(88 + z);
			MAtk[z] = loadMAtk(z + 88);
			PAtk[z] = loadPAtk(z + 88);
			PDef[z] = loadPDef(z + 88);
			MDef[z] = loadMDef(z + 88);
			HP[z] = loadHP(z + 88);
			MP[z] = loadMP(z + 88);
			MAtkSpd[z] = loadMAtkSpd(z + 88);
			PAtkSpd[z] = loadPAtkSpd(z + 88);
		}

	}
	
	private static boolean isBalanceSchemaValid()
	{
		if (_balanceSchemaChecked)
		{
			return _balanceSchemaValid;
		}
		
		synchronized (BalanceLoad.class)
		{
			if (_balanceSchemaChecked)
			{
				return _balanceSchemaValid;
			}
			
			final Set<String> requiredColumns = Set.of("class_id", "ev", "patk", "matk", "pdef", "mdef", "hp", "mp", "matksp", "patksp");
			final Set<String> existingColumns = new HashSet<>();
			
			try (Connection con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement stm = con.prepareStatement("SHOW COLUMNS FROM balance");
				ResultSet rset = stm.executeQuery())
			{
				while (rset.next())
				{
					existingColumns.add(rset.getString("Field"));
				}
				
				_balanceSchemaValid = existingColumns.containsAll(requiredColumns);
				if (!_balanceSchemaValid)
				{
					System.err.println("BalanceLoad: table 'balance' has incompatible schema. Missing columns: " + missingColumns(requiredColumns, existingColumns) + ". Balance modifiers disabled.");
				}
			}
			catch (Exception e)
			{
				_balanceSchemaValid = false;
				System.err.println("BalanceLoad: couldn't validate table 'balance'. Balance modifiers disabled.");
				e.printStackTrace();
			}
			
			_balanceSchemaChecked = true;
			return _balanceSchemaValid;
		}
	}
	
	private static Set<String> missingColumns(Set<String> requiredColumns, Set<String> existingColumns)
	{
		final Set<String> missing = new HashSet<>(requiredColumns);
		missing.removeAll(existingColumns);
		return missing;
	}

	@SuppressWarnings("resource")
	public static int loadEvasion(int classId)
	{
		int i = 0;

		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement stm = con.prepareStatement("SELECT ev FROM balance WHERE class_id=" + classId);
			ResultSet rset = stm.executeQuery();

			if (rset.next())
			{
				i = rset.getInt("ev");
			}

			stm.close();
		}
		catch (Exception e)
		{
			System.err.println("Error while loading balance stats from database.");
			e.printStackTrace();
		}
		return i;
	}

	@SuppressWarnings("resource")
	public static int loadAccuracy(int classId)
	{
		int i = 0;

		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement stm = con.prepareStatement("SELECT acc FROM balance WHERE class_id=" + classId);
			ResultSet rset = stm.executeQuery();

			if (rset.next())
			{
				i = rset.getInt("acc");
			}

			stm.close();
		}
		catch (Exception e)
		{
			System.err.println("Error while loading balance stats from database.");
			e.printStackTrace();
		}
		return i;
	}

	@SuppressWarnings("resource")
	public static int loadPAtk(int classId)
	{
		int i = 0;
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement stm = con.prepareStatement("SELECT patk FROM balance WHERE class_id=" + classId);
			ResultSet rset = stm.executeQuery();

			if (rset.next())
			{
				i = rset.getInt("patk");
			}

			stm.close();
		}
		catch (Exception e)
		{
			System.err.println("Error while loading balance stats from database.");
			e.printStackTrace();
		}
		return i;
	}

	@SuppressWarnings("resource")
	public static int loadMAtk(int classId)
	{
		int i = 0;

		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement stm = con.prepareStatement("SELECT matk FROM balance WHERE class_id=" + classId);
			ResultSet rset = stm.executeQuery();

			if (rset.next())
			{
				i = rset.getInt("matk");
			}

			stm.close();
		}
		catch (Exception e)
		{
			System.err.println("Error while loading balance stats from database.");
			e.printStackTrace();
		}
		return i;
	}

	@SuppressWarnings("resource")
	public static int loadPDef(int classId)
	{
		int i = 0;

		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement stm = con.prepareStatement("SELECT pdef FROM balance WHERE class_id=" + classId);
			ResultSet rset = stm.executeQuery();

			if (rset.next())
			{
				i = rset.getInt("pdef");
			}

			stm.close();
		}
		catch (Exception e)
		{
			System.err.println("Error while loading balance stats from database.");
			e.printStackTrace();
		}

		return i;
	}

	@SuppressWarnings("resource")
	public static int loadMDef(int classId)
	{
		int i = 0;

		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement stm = con.prepareStatement("SELECT mdef FROM balance WHERE class_id=" + classId);
			ResultSet rset = stm.executeQuery();

			if (rset.next())
			{
				i = rset.getInt("mdef");
			}

			stm.close();
		}
		catch (Exception e)
		{
			System.err.println("Error while loading balance stats from database.");
			e.printStackTrace();
		}

		return i;
	}

	@SuppressWarnings("resource")
	public static int loadHP(int classId)
	{
		int i = 0;

		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement stm = con.prepareStatement("SELECT hp FROM balance WHERE class_id=" + classId);
			ResultSet rset = stm.executeQuery();

			if (rset.next())
			{
				i = rset.getInt("hp");
			}

			stm.close();
		}
		catch (Exception e)
		{
			System.err.println("Error while loading balance stats from database.");
			e.printStackTrace();
		}

		return i;
	}



	@SuppressWarnings("resource")
	public static int loadMP(int classId)
	{
		int i = 0;

		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement stm = con.prepareStatement("SELECT mp FROM balance WHERE class_id=" + classId);
			ResultSet rset = stm.executeQuery();

			if (rset.next())
			{
				i = rset.getInt("mp");
			}

			stm.close();
		}
		catch (Exception e)
		{
			System.err.println("Error while loading balance stats from database.");
			e.printStackTrace();
		}

		return i;
	}

	@SuppressWarnings("resource")
	public static int loadMAtkSpd(int classId)
	{
		int i = 0;

		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement stm = con.prepareStatement("SELECT matksp FROM balance WHERE class_id=" + classId);
			ResultSet rset = stm.executeQuery();

			if (rset.next())
			{
				i = rset.getInt("matksp");
			}

			stm.close();
		}
		catch (Exception e)
		{
			System.err.println("Error while loading balance stats from database.");
			e.printStackTrace();
		}

		return i;
	}

	@SuppressWarnings("resource")
	public static int loadPAtkSpd(int classId)
	{
		int i = 0;

		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement stm = con.prepareStatement("SELECT patksp FROM balance WHERE class_id=" + classId);
			ResultSet rset = stm.executeQuery();

			if (rset.next())
			{
				i = rset.getInt("patksp");
			}

			stm.close();
		}
		catch (Exception e)
		{
			System.err.println("Error while loading balance stats from database.");
			e.printStackTrace();
		}

		return i;
	}
}