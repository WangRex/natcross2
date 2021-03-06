package person.pluto.natcross2.api.socketpart;

import java.io.IOException;
import java.net.Socket;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import person.pluto.natcross2.api.IBelongControl;
import person.pluto.natcross2.api.passway.SimplePassway;

/**
 * 
 * <p>
 * socket匹配对
 * </p>
 *
 * @author Pluto
 * @since 2019-07-12 08:36:30
 */
@Slf4j
public class SimpleSocketPart extends AbsSocketPart implements IBelongControl {

	protected SimplePassway outToInPassway;
	protected SimplePassway inToOutPassway;

	@Getter
	@Setter
	private int streamCacheSize = 8192;

	public SimpleSocketPart(IBelongControl belongThread) {
		super(belongThread);
	}

	@Override
	public boolean isValid() {
		if (super.isValid()) {
			if (this.outToInPassway == null || !this.outToInPassway.isValid() || this.inToOutPassway == null
					|| !this.inToOutPassway.isValid()) {
				return false;
			}
			return this.isAlive;
		}
		return false;
	}

	/**
	 ** 停止，并告知上层处理掉
	 *
	 * @author Pluto
	 * @since 2019-07-11 17:04:52
	 */
	public void stop() {
		this.cancel();

		IBelongControl belong;
		if ((belong = this.belongThread) != null) {
			this.belongThread = null;
			belong.stopSocketPart(this.socketPartKey);
		}
	}

	@Override
	public void cancel() {
		if (!this.isAlive) {
			return;
		}
		this.isAlive = false;

		log.debug("socketPart {} will cancel", this.socketPartKey);

		Socket recvSocket;
		if ((recvSocket = this.recvSocket) != null) {
			this.recvSocket = null;
			try {
				recvSocket.close();
			} catch (IOException e) {
				log.debug("socketPart [{}] 监听端口 关闭异常", socketPartKey);
			}
		}

		Socket sendSocket;
		if ((sendSocket = this.sendSocket) != null) {
			this.sendSocket = null;
			try {
				sendSocket.close();
			} catch (IOException e) {
				log.debug("socketPart [{}] 发送端口 关闭异常", socketPartKey);
			}
		}

		SimplePassway outToInPassway;
		if ((outToInPassway = this.outToInPassway) != null) {
			this.outToInPassway = null;
			outToInPassway.cancel();
		}
		SimplePassway inToOutPassway;
		if ((inToOutPassway = this.inToOutPassway) != null) {
			this.inToOutPassway = null;
			inToOutPassway.cancel();
		}

		log.debug("socketPart {} is cancelled", this.socketPartKey);
	}

	@Override
	public boolean createPassWay() {
		if (this.isAlive) {
			return true;
		}
		this.isAlive = true;

		try {
			SimplePassway outToInPassway = this.outToInPassway = new SimplePassway();
			outToInPassway.setBelongControl(this);
			outToInPassway.setRecvSocket(this.recvSocket);
			outToInPassway.setSendSocket(this.sendSocket);
			outToInPassway.setStreamCacheSize(getStreamCacheSize());

			SimplePassway inToOutPassway = this.inToOutPassway = new SimplePassway();
			inToOutPassway.setBelongControl(this);
			inToOutPassway.setRecvSocket(this.sendSocket);
			inToOutPassway.setSendSocket(this.recvSocket);
			inToOutPassway.setStreamCacheSize(getStreamCacheSize());

			outToInPassway.start();
			inToOutPassway.start();
		} catch (Exception e) {
			log.error("socketPart [" + this.socketPartKey + "] 隧道建立异常", e);
			this.stop();
			return false;
		}
		return true;
	}

	@Override
	public void noticeStop() {
		this.stop();
	}

}
