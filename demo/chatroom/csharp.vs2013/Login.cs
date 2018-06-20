using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace chatroom
{
    public partial class Login : Form
    {
        public Login()
        {
            InitializeComponent();
            try
            {
                lastport = int.Parse(textBoxServerPort.Text.Trim());
                lastporttext = lastport.ToString();
            }
            catch (Exception)
            {
                lastport = 10000;
                lastporttext = lastport.ToString();
            }
        }

        private void buttonOK_Click(object sender, EventArgs e)
        {
            if (getUsername().Length > 0 && getToken().Length > 0)
                DialogResult = DialogResult.OK;
        }

        public string getUsername()
        {
            return textBoxUsername.Text.Trim();
        }

        public string getToken()
        {
            return textBoxToken.Text.Trim();
        }

        public string getPlatFlag()
        {
            return textBoxPlatFlag.Text.Trim();
        }

        public string getServerIP()
        {
            return textBoxServerIP.Text.Trim();
        }

        public int getServerPort()
        {
            return lastport;
        }

        private string lastporttext;
        private int lastport;
        private void textBoxServerPort_TextChanged(object sender, EventArgs e)
        {
            try
            {
                lastport = int.Parse(textBoxServerPort.Text.Trim());
                lastporttext = lastport.ToString();
            }
            catch(Exception)
            {}
            textBoxServerPort.Text = lastporttext;
        }

    }
}
