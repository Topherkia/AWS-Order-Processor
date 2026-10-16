import os
import pymysql
from datetime import datetime

class DatabaseManager:
    """
    Shared database utility class for RDS MySQL interactions within an AWS VPC.
    """
    def init(self):
        self.host = os.environ.get('DB_HOST')
        self.user = os.environ.get('DB_USER')
        self.password = os.environ.get('DB_PASSWORD')
        self.database = os.environ.get('DB_NAME')
        self.port = int(os.environ.get('DB_PORT', 3306))

    def _get_connection(self):
        return pymysql.connect(
            host=self.host,
            user=self.user,
            password=self.password,
            database=self.database,
            port=self.port,
            cursorclass=pymysql.cursors.DictCursor,
            connect_timeout=5
        )
    
    def get_items(self, payload):
        connection = self._get_connection()
        try:
            with connection.cursor() as cursor:
                query = "SELECT * FROM items"
                cursor.execute(query)
            connection.commit()
            return cursor.fetchall()
        except Exception as e:
            connection.rollback()
            raise e
        finally:
            connection.close()
    def get_orders(self, payload):
        connection = self._get_connection()
        try:
            with connection.cursor() as cursor:
                query = "SELECT * FROM orders WHERE user_id = %s"
                cursor.execute(query, (payload["user_id"],))
            connection.commit()
            return cursor.fetchall()
        except Exception as e:
            connection.rollback()
            raise e
        finally:
            connection.close()
    def get_notifications(self, payload):
        connection = self._get_connection()
        try:
            with connection.cursor() as cursor:
                query = "SELECT * FROM notifications WHERE user_id = %s"
                cursor.execute(query, (payload["user_id"],))
            connection.commit()
            return cursor.fetchall()
        except Exception as e:
            connection.rollback()
            raise e
        finally:
            connection.close()
    def login(self, payload):
        connection = self._get_connection()
        try:
            with connection.cursor() as cursor:
                query = "SELECT id FROM customers WHERE email = %s limit 1"
                cursor.execute(query, (payload["email"],))
            connection.commit()
            return cursor.fetchall()
        except Exception as e:
            connection.rollback()
            raise e
        finally:
            connection.close()
    def logout(self, payload):
        return True
