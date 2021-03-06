package erp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;


/*
 * 
CREATE TABLE orders (
id SERIAL PRIMARY KEY,
number INT,
client VARCHAR(2),
workpiece INT,
quantity INT,
due_date INT,
late_penalty INT,
early_penalty INT
);
 * 
 * 
DELETE FROM orders;
DELETE FROM day_pieces_intermed;
DELETE FROM orders_to_supplier;
DELETE FROM production_plan;
ALTER SEQUENCE orders_to_supplier_id_seq RESTART WITH 1;
ALTER SEQUENCE orders_id_seq RESTART WITH 1; 
 * 
 */

//https://www.tutorialspoint.com/postgresql/postgresql_java.htm
public class Database {
	
	public int Dd = 0;

	public Connection ConnectDB() {
		Connection c = null;
	      try {
	    	  Class.forName("org.postgresql.Driver");
	    	  String url = "jdbc:postgresql://db.fe.up.pt:5432/up201806577";
	    	  String username = "up201806577";
	    	  String password = "dpPk0HcRN";
	    	  
	    	  c = DriverManager.getConnection(url,username,password);
	    	  	    	  
	    	  //System.out.println(c.getSchema());
	    	  //c.close();
	      } catch (Exception e) {
	    	  e.printStackTrace();
	          System.err.println(e.getClass().getName()+": "+e.getMessage());
	          System.exit(0);
	      }
	      
	      return c;
	}
	
	public void insertOrder(order order) throws SQLException {
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
			
		String sql = "INSERT INTO Orders "
						+ "(Number,Client,WorkPiece,Quantity,Due_Date,Late_Penalty,Early_Penalty) "
		        		+ "VALUES ('"+order.getNumber()+"','"
		        					 +order.getClient()+"','"
		        					 +order.getWorkpiece()+"','"
		        					 +order.getQty()+"','"
		        					 +order.getDueDate()+"','"
		        					 +order.getLatePenalty()+"','"
		        					 +order.getEarlyPenalty()+"');";
		stmt.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
		
		ResultSet rs = stmt.getGeneratedKeys();
		rs.next();
		order.setId(rs.getInt(1));
		
		productionPlan(order);
		
	}
	
	public int updateDate(int day) throws SQLException {
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		String sql = "UPDATE day set day = "+day+";";
		
		stmt.executeUpdate(sql);
		return day;
	}
	
	public void startWarehouse() throws SQLException {
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		String sql = "UPDATE warehouse SET quantity = 12 WHERE piece='1';";
		stmt.executeUpdate(sql);
		sql = "UPDATE warehouse SET quantity = 12 WHERE piece='2';";
		stmt.executeUpdate(sql);
	}
	
	public int getDate() throws SQLException {
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		int date = 0;
		ResultSet rs = stmt.executeQuery( "SELECT * FROM day;" );
		while(rs.next()) {
			date = (rs.getInt("day"));
		}
		return date;
	}
	
	public plan productionPlan(order order) throws SQLException {
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		String sql;
		MPS mps = new MPS(); 
		supplier s = new supplier();
		plan prod_plan = new plan();
		float cost = 0;
		
		prod_plan.setId(order.getId());
		prod_plan.setNumber(order.getNumber());
		prod_plan.setClient(order.getClient());
		prod_plan.setQty(order.getQty());
		prod_plan.setFinalForm(order.getWorkpiece());
		
		int starting_piece = prod_plan.set_start_piece(prod_plan.getFinalForm());
		prod_plan.setStartingForm(starting_piece);
		
		prod_plan.setDueDate(order.getDueDate());
		prod_plan.setPlcmntDate(getDate());
		prod_plan.setStartDate(mps.set_start_date(prod_plan.getId()));
		
		//updateNrOfPiecesInWarehouse(prod_plan.getStartingForm(), prod_plan.getQty());
		int warehouse_pieces = numberOfPiecesInWarehouse(prod_plan.getStartingForm());
		s = mps.orderPieces(prod_plan.getStartingForm(), prod_plan.getQty());
		int pieces_ordered = s.getQty();
		
		cost = cost(prod_plan.getId(), s, 0);
			
		sql = "INSERT INTO production_plan (order_id,number,client,final_form,starting_piece,quantity,start_date,days_to_finish,warehouse_pieces,pieces_ordered,cost) "
        		+ "VALUES ("+prod_plan.getId()+","
        					 +prod_plan.getNumber()+",'"
        					 +prod_plan.getClient()+"',"
							 +prod_plan.getFinalForm()+","
        					 +prod_plan.getStartingForm()+","
							 +prod_plan.getQty()+","
							 +prod_plan.getStartDate()+","
        					 +(prod_plan.getDueDate()-getDate())+","
        					 +warehouse_pieces+","
        					 +pieces_ordered+","
        					 +cost+");";
		stmt.executeUpdate(sql);
		
		return prod_plan;
	}
	
	public plan sendToMES(plan plan) throws SQLException {
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		String sql;
		
		int warehouse_pieces = 0;
		int pieces_ordered = 0;
		
		ResultSet rs = stmt.executeQuery( "SELECT * FROM production_plan WHERE order_id="+plan.getId()+";" );
		
		while(rs.next()) {
			warehouse_pieces = rs.getInt("warehouse_pieces");
			pieces_ordered = rs.getInt("pieces_ordered");
		}
		
		sql = "INSERT INTO day_pieces_intermed (order_id,priority,final_form,days_to_finish,nr_pieces,client_id,start_date,warehouse_pieces,pieces_ordered) "
        		+ "VALUES ('"+plan.getId()+"','"
        					 +1+"','"
        					 +plan.getFinalForm()+"','"
        					 +(plan.getDueDate()-getDate())+"','"
        					 +plan.getQty()+"','"
        					 +plan.getClient()+"','"
        					 +plan.getStartDate()+"','"
        					 +warehouse_pieces+"','"
        					 +pieces_ordered+"');";
		stmt.executeUpdate(sql);
		
		return plan;
	}
	
	public void getPlan() throws SQLException {
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		
		plan plan = new plan();
		
		ResultSet rs = stmt.executeQuery( "SELECT * FROM production_plan;" );
		
		while(rs.next()) {
			plan.setId(rs.getInt("order_id"));
			plan.setNumber(rs.getInt("number"));
			plan.setClient(rs.getString("client"));
			plan.setFinalForm(rs.getInt("final_form"));
			plan.setStartingForm(rs.getInt("starting_piece"));
			plan.setDueDate(rs.getInt("days_to_finish")+getDate());
			plan.setStartDate(rs.getInt("start_date"));
			plan.setQty(rs.getInt("quantity"));
			plan.setCost(rs.getInt("cost"));
			
			if(checkEmpty() == true) {
				if(plan.getStartDate() <= getDate()) {
					sendToMES(plan);
					deleteOrder(plan);
					updateNrOfPiecesInWarehouse(plan.getStartingForm(),plan.getQty());
					System.out.println("*****************************************");
					System.out.println("* START MANUFACTURING ORDER NUMBER "+plan.getNumber()+"	*");
					System.out.println("* Client: "+plan.getClient()+"				*");
				    System.out.println("* Due date: "+plan.getDueDate()+"				*");
				    System.out.println("* Pieces: "+plan.getQty()+" P"+plan.getFinalForm()+"				*");
					System.out.println("*****************************************");
				}
			}	
		}
	}
	
	public plan deleteOrder(plan plan) throws SQLException {
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		String sql = "DELETE FROM production_plan WHERE order_id="+plan.getId()+";";
		stmt.executeUpdate(sql);
		return plan;
	}
	
	public boolean checkEmpty() throws SQLException {
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		boolean empty = false;
		int count = 0;
		
		String sql = "SELECT COUNT(*) FROM day_pieces_intermed;";
		ResultSet rs = stmt.executeQuery(sql);
		while(rs.next()) {
			count = rs.getInt(1);
		}
		if (count > 0) empty = false; 
		else {
			empty = true;
			Dd = getDate();
		}
		return empty;
	}
	
	public void selectStats() throws SQLException {
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		int id = 0;
		int date = 0;
		String client = null;
		
		String sql = "SELECT * FROM day_stats;";
		ResultSet rs = stmt.executeQuery(sql);
		while(rs.next()) {
			id = rs.getInt("id");
			client = rs.getString("client");
			date = rs.getInt("date");
		}
		insertCost(id, client, date);
	}
	
	public order getOrder(int id) throws SQLException {
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		
		order order = new order();
		
		ResultSet rs = stmt.executeQuery( "SELECT * FROM orders WHERE id='"+id+"';" );
		
		while(rs.next()) {
			order.setNumber(rs.getInt("number"));
			order.setClient(rs.getString("client"));
			order.setWorkpiece(rs.getInt("workpiece"));
			order.setQty(rs.getInt("quantity"));
			order.setDueDate(rs.getInt("due_date"));
			order.setLatePenalty(rs.getInt("late_penalty"));
			order.setEarlyPenalty(rs.getInt("early_penalty"));
		}
		
		return order;
	}
	
	public void insertOrdersToSupplier(supplier s) throws SQLException {
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
			
		String sql = "INSERT INTO orders_to_supplier "
						+ "(supplier,piece,quantity,price,dlvr_date) "
		        		+ "VALUES ('"+s.getSupplier()+"',"
		        					 +s.getPiece()+","
		        					 +s.getQty()+","
		        					 +s.getPrice()+","
		        					 +s.getDlvrDate()+");";
		stmt.executeUpdate(sql);
		
		if(s.getQty() > 0) {
			System.out.println();
			System.out.println("Ordered "+s.getQty()+" P"+s.getPiece()+" from supplier "+s.getSupplier());
			System.out.println();
		}
	}
	
	public int numberOfPiecesInWarehouse(int piece_id) throws SQLException {
		int qty = 0;
		
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		
		ResultSet rs = stmt.executeQuery( "SELECT Quantity FROM warehouse WHERE piece='"+piece_id+"';");
		
		while(rs.next()) {
			qty = rs.getInt("quantity");
		}
		
		return qty;
	}
	
	public void updateNrOfPiecesInWarehouse(int piece, int qty) throws SQLException {
		int new_qty = numberOfPiecesInWarehouse(piece)-qty;
		
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		
		String sql = "UPDATE warehouse SET quantity = "+new_qty+" WHERE piece = "+piece+";";
		
		System.out.println();
		System.out.println("-----------------------------------------");
		System.out.println("Number of P"+piece+" in warehouse: "+new_qty);
		System.out.println("-----------------------------------------");
		System.out.println();
		
		stmt.executeUpdate(sql);
	}
	
	public void checkForDelivery() throws SQLException {
		int piece = 0;
		int qty = 0;
		int dlvr = 0;
		
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		
		ResultSet rs = stmt.executeQuery( "SELECT * FROM orders_to_supplier;");
		
		while(rs.next()) {
			piece = rs.getInt("piece");
			qty = rs.getInt("quantity");
			dlvr = rs.getInt("dlvr_date");
			
			if((dlvr == getDate()) && (qty > 0)) {
				System.out.println();
				System.out.println("Received "+qty+" P"+piece+" in warehouse");
				System.out.println();
				updateNrOfPiecesInWarehouse(piece, -qty);
			}
		}
	}
	
	public float cost(int id, supplier s, int Dd) throws SQLException {
		int Pt = 0; //production time in secs
		int penalty = 0;
		int Rc = s.getPrice()*s.getQty(); //material raw cost
		int Ad = s.getDlvrDate()-1;
		
		order order = getOrder(id);
		
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		
		ResultSet rs = stmt.executeQuery( "SELECT avg_time_piece, date FROM day_stats WHERE orderid ='"+order.getId()+"';");
		while(rs.next()) {
			Pt = rs.getInt("avg_time_piece");
			Dd = rs.getInt("date");
		}
		
		int Pc = Pt * 1;//production cost
		int Dc = Rc + (Dd - Ad);
		
		if(Dd > order.getDueDate()) penalty = order.getLatePenalty()*(Dd-order.getDueDate());
		else penalty = order.getEarlyPenalty()*(order.getDueDate()-Dd);
		
		int Tc = (Rc + Pc + Dc) + penalty; //total cost Tc = Rc + Pc + Dc
		
		//insertCost(order.getId(), order.getNumber(), order.getClient(), Tc);
		
		return Tc;
	}
	
	public float insertCost(int id, String client, int Dd) throws SQLException {
		Connection c = ConnectDB();
		Statement stmt = c.createStatement();
		order order = getOrder(id);
		
		int Pt = 0; //production time in secs
		int penalty = 0;
		int Rc = 55*order.getQty(); //material raw cost
		int Ad = Dd-1;
		
		ResultSet rs = stmt.executeQuery( "SELECT avg_time_piece, date FROM day_stats WHERE orderid ='"+order.getId()+"';");
		while(rs.next()) {
			Pt = rs.getInt("avg_time_piece");
			Dd = rs.getInt("date");
		}
		
		int Pc = Pt * 1;//production cost
		int Dc = Rc + (Dd - Ad);
		
		if(Dd > order.getDueDate()) penalty = order.getLatePenalty()*(Dd-order.getDueDate());
		else penalty = order.getEarlyPenalty()*(order.getDueDate()-Dd);
		
		int Tc = (Rc + Pc + Dc) + penalty; //total cost Tc = Rc + Pc + Dc
		
		String sql = "INSERT costs (id,number,client,cost) VALUES ('"+id+"','"+order.getNumber()+"','"+client+"','"+Tc+"');";
		stmt.executeUpdate(sql);
		
		return Tc;
	}
	
}
