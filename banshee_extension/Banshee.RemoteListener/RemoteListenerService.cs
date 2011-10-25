using System;
using System.Net;
using System.Net.Sockets;
using System.Reflection;

using Mono.Unix;
using Banshee.Base;
using Banshee.Configuration;
using Banshee.Preferences;
using Banshee.ServiceStack;

using Hyena;

namespace Banshee.RemoteListener
{
	/// <summary>
	/// Remote control extension for banshee player.
	/// </summary>
	/// This extension is starting a socket listener and controls banshee player by receiving and
	/// handling incoming requests.
	public class RemoteListenerService : IExtensionService, IDisposable
	{
		#region Attributes
		
		/// <summary>
		/// Remote control socket listener.
		/// </summary>
		private Socket _listener;
		
		/// <summary>
		/// Contains the required request password ID which was specified in banshee.
		/// </summary>
		private int _passId;
		
		/// <summary>
		/// Banshee port preference.
		/// </summary>
		private PreferenceBase _portPref;
		
		/// <summary>
		/// Banshee preferences
		/// </summary>
		private PreferenceService _prefs;
		
		#endregion
		
		
		#region Banshee extension

		string IService.ServiceName { get { return "RemoteServer"; } }

		void IExtensionService.Initialize()
		{
			_prefs = ServiceManager.Get<PreferenceService>();
			
			if (_prefs == null) {
				return;
			}
			
			Page remoteControlPage = new Page("RemoteControl", "Remote Control", 3);
			_prefs.FindOrAdd(remoteControlPage);
			
			Section BansheeRemotePrefs = remoteControlPage.FindOrAdd(
				new Section("BansheeRemote", "Banshee Remote", 0));
			
			_portPref = BansheeRemotePrefs.Add(new SchemaPreference<int>(
				RemotePortSchema,
				Catalog.GetString("Port"),
			    Catalog.GetString("Banshee will listen for remote control requests on this port")
			));
			
			_portPref = BansheeRemotePrefs.Add(new SchemaPreference<int>(
				RemotePassIdSchema,
				Catalog.GetString("Password ID"),
			    Catalog.GetString("\"Secret\" ID which is required to be specified in incoming requests")
			));
			
			_prefs["RemoteControl"]["BansheeRemote"]["remote_control_passid"].ValueChanged += delegate {
				_passId = (int) _prefs["RemoteControl"]["BansheeRemote"]["remote_control_passid"].BoxedValue;
			};
			
			_prefs["RemoteControl"]["BansheeRemote"]["remote_control_port"].ValueChanged += delegate {
				StartRemoteListener();
			};
			
			Helper.SetDbCompressTimeFromFile();
			
			_passId = (int) _prefs["RemoteControl"]["BansheeRemote"]["remote_control_passid"].BoxedValue;
			
			ServiceManager.StartupFinished += delegate {
				Helper.CompressDatabase();
				Helper.GetOrCreateRemotePlaylist();
				StartRemoteListener();
			};
		}

		void IDisposable.Dispose()
		{
			_prefs["RemoteControl"]["BansheeRemote"].Remove(_portPref);
			_prefs["RemoteControl"].Remove (_prefs["RemoteControl"].FindById("BansheeRemote"));
			_prefs.Remove(_prefs.FindById("RemoteControl"));
			
			if (_listener != null) {
				try {
					_listener.Close();
				} catch (Exception e) {
					Log.Error("error while closing socket of remote listener: " + e.Message);
				}
			}
		}

		public static readonly SchemaEntry<int> RemotePortSchema = new SchemaEntry<int>(
			"remote_control", "remote_control_port", 8484, 1024, 49151, "", ""
		);
		
		public static readonly SchemaEntry<int> RemotePassIdSchema = new SchemaEntry<int>(
			"remote_control", "remote_control_passid", 0, 0, 65536, "", ""
		);

		#endregion

		
		#region RemoteListener connector

		/// <summary>
		/// Start remote control socket listener with port given in the preferences.
		/// </summary>
		public void StartRemoteListener ()
		{
			int port = (int) _prefs["RemoteControl"]["BansheeRemote"]["remote_control_port"].BoxedValue;
			
			if (_listener != null) {
				_listener.Disconnect(false);
			}
			
			try {
				Log.Information("remote listener start listening on port " + port.ToString());
				IPEndPoint endpoint = new IPEndPoint(IPAddress.Any, port);
				
				_listener = new Socket(AddressFamily.InterNetwork,
						SocketType.Stream, ProtocolType.Tcp);
				_listener.Bind(endpoint);
				_listener.Listen(10);
				_listener.BeginAccept(OnIncomingConnection, _listener);
			} catch (Exception e) {
				Log.Error("error while starting remote listener", e.Message);
			}
		}

		/// <summary>
		/// Trigger on incomming client request.
		/// </summary>
		/// <param name="ar">
		/// Contains the client socket.
		/// </param>
		void OnIncomingConnection(IAsyncResult ar)
		{
			Socket client = null;
			
			try {
				client = ((Socket)ar.AsyncState).EndAccept(ar);
				client.BeginReceive(Helper.Buffer, 0, Helper.Buffer.Length, SocketFlags.None,
					OnReceiveRequest, client);
			} catch (Exception e) {
				Log.Error("error while handling client request by remote listener: " + e.Message);
				_listener.BeginAccept(new AsyncCallback(OnIncomingConnection), _listener);
			}
		}
		
		/// <summary>
		/// Triggered when received client request was read.
		/// </summary>
		/// <param name="ar">
		/// Contains the client socket.
		/// </param>
		void OnReceiveRequest(IAsyncResult ar) {
			Socket client = null;
			bool isListenerAccepting = false;
			
			try {
				client = (Socket)ar.AsyncState;
				int readBytes = client.EndReceive(ar);
				
				byte [] result = null;
				string requestName = ((RequestHandler.RequestCode) Helper.Buffer[0]).ToString();
				
				if (Helper.ShortFromBuffer(1) == _passId) {
					Helper.StripGlobalInfoFromBuffer(readBytes);
					result = (byte []) typeof(RequestHandler).GetMethod(requestName).Invoke(
						null,new object [] {readBytes - 3});
				} else if ((RequestHandler.RequestCode) Helper.Buffer[0] == RequestHandler.RequestCode.Test) {
					result = new byte [] {0};
				}
				
				// we handled the request and have the data, handle other requests now
				isListenerAccepting = true;
				_listener.BeginAccept(new AsyncCallback(OnIncomingConnection), _listener);
				
				if (result != null && result.Length != 0) {
					client.BeginSend(result, 0, result.Length, SocketFlags.None, 
					                 OnSentResponse, client);
				}
			} catch (Exception e) {
				Log.Error("remote listener request error", e.Message);
				
				if (!isListenerAccepting) {
					try {
						// error occurred on request handle listening was not started
						_listener.BeginAccept(new AsyncCallback(OnIncomingConnection), _listener);
					} catch (Exception e2) {
						Log.Error("error while starting accepting of remote listener", e2.Message);
					}
				}
			}
		}
		
		/// <summary>
		/// Triggered when response was sent back to client.
		/// </summary>
		/// <param name="ar">
		/// Contains the client socket.
		/// </param>
		void OnSentResponse(IAsyncResult ar) {
			try {
				((Socket)ar.AsyncState).Close();
			} catch {
			}
		}
		
		#endregion
	}
}
